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

    public static final String PREFS_NAME = "profile_prefs";

    // Las claves de foto son por UID para evitar que la foto de un usuario
    // aparezca al cambiar de cuenta en el mismo dispositivo.

    /** Clave para la ruta local de la foto del usuario con el UID dado. */
    public static String photoPathKey(String uid) {
        return "local_photo_path_" + uid;
    }

    /** Clave para la signatura de caché de Glide del usuario con el UID dado. */
    public static String photoSigKey(String uid) {
        return "photo_signature_" + uid;
    }

    // ── Listener ───────────────────────────────────────────────────────────────

    public interface OnProfileUpdatedListener {
        void onProfileUpdated(String newName, File localPhoto);
    }
    private OnProfileUpdatedListener listener;

    // ── Vistas ─────────────────────────────────────────────────────────────────

    private ImageView         ivProfilePhoto;
    private TextInputLayout   tilDisplayName;
    private TextInputEditText etDisplayName;
    private MaterialButton    btnSaveProfile;
    private MaterialButton    btnCancelProfile;
    private View              loadingOverlay;
    private View              btnChangePhoto;

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
        return inflater.inflate(R.layout.dialog_profile_edit, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

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

    private void loadCurrentPhoto() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { ivProfilePhoto.setImageResource(R.drawable.ic_person_24); return; }

        // Leer con clave específica del UID actual
        SharedPreferences prefs = requireContext()
                .getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE);
        String localPath = prefs.getString(photoPathKey(user.getUid()), null);
        long   signature = prefs.getLong(photoSigKey(user.getUid()), 0L);

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

        if (user.getPhotoUrl() != null) {
            ivProfilePhoto.clearColorFilter();
            Glide.with(this).load(user.getPhotoUrl())
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
                    Glide.with(this)
                            .load(dest)
                            .skipMemoryCache(true)
                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                            .transform(new CircleCrop())
                            .into(ivProfilePhoto);
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

        // Persistir foto antes de Firebase, con clave específica del UID.
        // Si Firebase falla o la Activity se recrea por cambio de tema,
        // la foto ya está guardada con su signatura correcta.
        if (selectedPhotoFile != null && selectedPhotoFile.exists()) {
            long newSig = System.currentTimeMillis();
            requireContext()
                    .getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putString(photoPathKey(user.getUid()), selectedPhotoFile.getAbsolutePath())
                    .putLong(photoSigKey(user.getUid()), newSig)
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
                    Toast.makeText(requireContext(),
                            "Guardado localmente. Sin conexión con el servidor.",
                            Toast.LENGTH_LONG).show();
                    if (listener != null) listener.onProfileUpdated(newName, selectedPhotoFile);
                    dismiss();
                });
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private File getProfilePhotoFile() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        String uid = (u != null) ? u.getUid() : "anon";
        // Cada usuario tiene su propio archivo de foto para no solaparse
        File dir = new File(requireContext().getFilesDir(), "profile");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, "avatar_" + uid + ".jpg");
    }

    private void setLoading(boolean loading) {
        if (loadingOverlay != null)
            loadingOverlay.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSaveProfile  .setEnabled(!loading);
        btnCancelProfile.setEnabled(!loading);
    }
}