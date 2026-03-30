package com.example.clef.ui.settings;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.signature.ObjectKey;
import com.example.clef.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProfileEditDialog extends BottomSheetDialogFragment {

    // ── Constantes SharedPreferences ───────────────────────────────────────────

    public static final String PREFS_NAME     = "profile_prefs";
    public static final String KEY_PHOTO_PATH = "local_photo_path";
    /**
     * Timestamp que se actualiza cada vez que se guarda una foto nueva.
     * Glide lo usa como parte de la cache key → al cambiar, invalida la caché.
     * Esto es lo que soluciona el bug de "la foto no cambia al cambiar de tema".
     */
    public static final String KEY_PHOTO_SIG  = "photo_signature";

    // ── Listener ───────────────────────────────────────────────────────────────

    public interface OnProfileUpdatedListener {
        void onProfileUpdated(String newName, File localPhoto);
    }
    private OnProfileUpdatedListener listener;

    // ── Vistas — IDs del layout dialog_profile_edit.xml ───────────────────────

    private ImageView         ivProfilePhoto;   // R.id.ivProfilePhoto
    private TextInputLayout   tilDisplayName;   // R.id.tilDisplayName
    private TextInputEditText etDisplayName;    // R.id.etDisplayName
    private MaterialButton    btnSaveProfile;   // R.id.btnSaveProfile
    private MaterialButton    btnCancelProfile; // R.id.btnCancelProfile
    private View              loadingOverlay;   // R.id.loadingOverlay
    private View              btnChangePhoto;   // R.id.btnChangePhoto

    // ── Estado ─────────────────────────────────────────────────────────────────

    private File selectedPhotoFile = null;
    private Uri  cameraUri         = null;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // ── Launchers ──────────────────────────────────────────────────────────────

    private final ActivityResultLauncher<Intent> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK
                        && result.getData() != null
                        && result.getData().getData() != null) {
                    handlePhotoSelected(result.getData().getData());
                }
            });

    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
                if (ok && cameraUri != null) handlePhotoSelected(cameraUri);
            });

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) launchCamera();
                else Toast.makeText(requireContext(),
                        "Se necesita permiso de cámara", Toast.LENGTH_SHORT).show();
            });

    // ── Factory ────────────────────────────────────────────────────────────────

    public static ProfileEditDialog newInstance() { return new ProfileEditDialog(); }

    public void setOnProfileUpdatedListener(OnProfileUpdatedListener l) { this.listener = l; }

    // ── Ciclo de vida ──────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // Usa el layout existente — dialog_profile_edit.xml
        return inflater.inflate(R.layout.dialog_profile_edit, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // IDs del layout dialog_profile_edit.xml
        ivProfilePhoto   = view.findViewById(R.id.ivProfilePhoto);
        tilDisplayName   = view.findViewById(R.id.tilDisplayName);
        etDisplayName    = view.findViewById(R.id.etDisplayName);
        btnSaveProfile   = view.findViewById(R.id.btnSaveProfile);
        btnCancelProfile = view.findViewById(R.id.btnCancelProfile);
        loadingOverlay   = view.findViewById(R.id.loadingOverlay);
        btnChangePhoto   = view.findViewById(R.id.btnChangePhoto);

        loadCurrentPhoto();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && user.getDisplayName() != null) {
            etDisplayName.setText(user.getDisplayName());
        }

        // Desactivar "Guardar" hasta que haya cambios
        btnSaveProfile.setEnabled(false);
        etDisplayName.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int i, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                String current = user != null && user.getDisplayName() != null
                        ? user.getDisplayName() : "";
                boolean nameChanged  = !s.toString().trim().equals(current);
                boolean photoChanged = selectedPhotoFile != null;
                btnSaveProfile.setEnabled(
                        (nameChanged || photoChanged) && s.toString().trim().length() >= 2);
            }
        });

        btnChangePhoto.setOnClickListener(v -> showPhotoOptions());
        ivProfilePhoto .setOnClickListener(v -> showPhotoOptions());
        btnSaveProfile .setOnClickListener(v -> onSave());
        btnCancelProfile.setOnClickListener(v -> dismiss());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    // ── Carga de foto actual ───────────────────────────────────────────────────

    /**
     * Carga la foto usando la signatura guardada.
     * Cuando la signatura cambia (nuevo timestamp tras guardar foto),
     * Glide descarta la caché y recarga el archivo del disco.
     * Esto resuelve el bug de "la foto no cambia al cambiar de tema".
     */
    private void loadCurrentPhoto() {
        SharedPreferences prefs = requireContext()
                .getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE);
        String localPath = prefs.getString(KEY_PHOTO_PATH, null);
        long   signature = prefs.getLong(KEY_PHOTO_SIG, 0L);

        if (localPath != null) {
            File f = new File(localPath);
            if (f.exists()) {
                ivProfilePhoto.clearColorFilter();
                Glide.with(this)
                        .load(f)
                        .signature(new ObjectKey(signature))
                        .skipMemoryCache(true)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .transform(new CircleCrop())
                        .placeholder(R.drawable.ic_person_24)
                        .into(ivProfilePhoto);
                return;
            }
        }

        // Fallback: foto de Firebase o placeholder
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && user.getPhotoUrl() != null) {
            ivProfilePhoto.clearColorFilter();
            Glide.with(this)
                    .load(user.getPhotoUrl())
                    .transform(new CircleCrop())
                    .placeholder(R.drawable.ic_person_24)
                    .into(ivProfilePhoto);
        } else {
            ivProfilePhoto.setImageResource(R.drawable.ic_person_24);
        }
    }

    // ── Selección de foto ──────────────────────────────────────────────────────

    private void showPhotoOptions() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Cambiar foto de perfil")
                .setItems(new String[]{"Cámara", "Galería"}, (d, which) -> {
                    if (which == 0) requestCameraPermission();
                    else openGallery();
                })
                .show();
    }

    private void requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            launchCamera();
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void launchCamera() {
        try {
            File tmp = File.createTempFile("cam_", ".jpg",
                    requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES));
            cameraUri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".provider", tmp);
            cameraLauncher.launch(cameraUri);
        } catch (IOException e) {
            Toast.makeText(requireContext(), "Error al abrir la cámara", Toast.LENGTH_SHORT).show();
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        galleryLauncher.launch(intent);
    }

    /**
     * Copia la imagen al directorio privado de la app y actualiza la preview.
     * Se ejecuta en hilo de fondo para no bloquear la UI.
     */
    private void handlePhotoSelected(Uri uri) {
        executor.execute(() -> {
            try {
                File dest = getProfilePhotoFile();
                try (InputStream in = requireContext().getContentResolver().openInputStream(uri);
                     FileOutputStream out = new FileOutputStream(dest)) {
                    if (in == null) throw new IOException("Stream nulo");
                    byte[] buf = new byte[4096];
                    int len;
                    while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
                }
                selectedPhotoFile = dest;

                requireActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    ivProfilePhoto.clearColorFilter();
                    // Preview inmediata — sin caché, recarga el archivo recién copiado
                    Glide.with(this)
                            .load(dest)
                            .skipMemoryCache(true)
                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                            .transform(new CircleCrop())
                            .into(ivProfilePhoto);
                    // Habilitar botón Guardar ahora que hay una foto nueva
                    btnSaveProfile.setEnabled(true);
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(),
                                "No se pudo cargar la imagen", Toast.LENGTH_SHORT).show());
            }
        });
    }

    // ── Guardar ────────────────────────────────────────────────────────────────

    private void onSave() {
        String newName = etDisplayName.getText() != null
                ? etDisplayName.getText().toString().trim() : "";
        if (newName.length() < 2) {
            tilDisplayName.setError("El nombre debe tener al menos 2 caracteres");
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { dismiss(); return; }

        setLoading(true);

        /**
         * IMPORTANTE: guardamos la ruta Y la signatura en SharedPreferences
         * ANTES de llamar a Firebase. Así, aunque Firebase tarde o la Activity
         * se recree por cambio de tema, la foto ya está persistida con una
         * signatura nueva → Glide invalidará la caché al recargar.
         */
        if (selectedPhotoFile != null && selectedPhotoFile.exists()) {
            long newSig = System.currentTimeMillis(); // timestamp único → nueva cache key
            requireContext()
                    .getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_PHOTO_PATH, selectedPhotoFile.getAbsolutePath())
                    .putLong(KEY_PHOTO_SIG, newSig)
                    .apply();
        }

        UserProfileChangeRequest.Builder builder =
                new UserProfileChangeRequest.Builder().setDisplayName(newName);
        if (selectedPhotoFile != null) {
            builder.setPhotoUri(Uri.fromFile(selectedPhotoFile));
        }

        user.updateProfile(builder.build())
                .addOnSuccessListener(unused -> {
                    setLoading(false);
                    Toast.makeText(requireContext(), "Perfil actualizado", Toast.LENGTH_SHORT).show();
                    if (listener != null) listener.onProfileUpdated(newName, selectedPhotoFile);
                    dismiss();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    // La foto local ya está guardada aunque Firebase falle
                    Toast.makeText(requireContext(),
                            "Guardado localmente. Sin conexión con el servidor.",
                            Toast.LENGTH_LONG).show();
                    if (listener != null) listener.onProfileUpdated(newName, selectedPhotoFile);
                    dismiss();
                });
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private File getProfilePhotoFile() {
        File dir = new File(requireContext().getFilesDir(), "profile");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, "avatar.jpg");
    }

    private void setLoading(boolean loading) {
        if (loadingOverlay != null)
            loadingOverlay.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSaveProfile  .setEnabled(!loading);
        btnCancelProfile.setEnabled(!loading);
    }
}