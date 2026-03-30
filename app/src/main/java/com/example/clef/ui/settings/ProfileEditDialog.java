package com.example.clef.ui.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
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
import java.io.InputStream;
import java.io.OutputStream;

public class ProfileEditDialog extends BottomSheetDialogFragment {

    // ── Interfaz de callback ───────────────────────────────────────────────────

    public interface OnProfileUpdatedListener {
        void onProfileUpdated(String newName, File localPhoto);
    }

    // ── Constantes SharedPreferences ───────────────────────────────────────────

    static final String PREFS_PROFILE  = "clef_profile";
    static final String KEY_PHOTO_PATH = "local_photo_path";

    // ── Vistas ─────────────────────────────────────────────────────────────────

    private ImageView         ivProfilePhoto;
    private View              btnChangePhoto;
    private TextInputLayout   tilDisplayName;
    private TextInputEditText etDisplayName;
    private MaterialButton    btnSave;
    private MaterialButton    btnCancel;
    private View              loadingOverlay;

    // ── Estado ─────────────────────────────────────────────────────────────────

    private File copiedPhotoFile = null;
    private OnProfileUpdatedListener listener;

    // ── Launcher de galería ────────────────────────────────────────────────────
    //
    // IMPORTANTE: usamos GetContent en lugar de ACTION_PICK + EXTERNAL_CONTENT_URI.
    // ACTION_PICK con setType() causa conflicto en muchos dispositivos y el
    // selector se cierra sin devolver nada. GetContent es el estándar moderno.

    private final ActivityResultLauncher<String> galleryLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.GetContent(),
                    uri -> {
                        if (uri == null) return; // usuario canceló

                        // Tomamos permiso persistente para poder leer el URI
                        // incluso después de que el BottomSheet se cierre
                        try {
                            requireContext().getContentResolver()
                                    .takePersistableUriPermission(
                                            uri,
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (Exception ignored) {
                            // Algunos proveedores no soportan permisos persistentes,
                            // no es crítico porque copiamos el archivo inmediatamente
                        }

                        // Copiamos el archivo AL MOMENTO mientras el URI es válido
                        copiedPhotoFile = copyUriToInternal(uri);

                        if (copiedPhotoFile != null) {
                            loadPhotoFromFile(copiedPhotoFile);
                        } else {
                            // Si la copia falló mostramos un error claro
                            Toast.makeText(requireContext(),
                                    "No se pudo cargar la imagen. Inténtalo de nuevo.",
                                    Toast.LENGTH_SHORT).show();
                        }

                        updateSaveButtonState(etDisplayName.getText());
                    });

    // ── Construcción ───────────────────────────────────────────────────────────

    public static ProfileEditDialog newInstance() {
        return new ProfileEditDialog();
    }

    public void setOnProfileUpdatedListener(OnProfileUpdatedListener l) {
        this.listener = l;
    }

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

        ivProfilePhoto = view.findViewById(R.id.ivProfilePhoto);
        btnChangePhoto = view.findViewById(R.id.btnChangePhoto);
        tilDisplayName = view.findViewById(R.id.tilDisplayName);
        etDisplayName  = view.findViewById(R.id.etDisplayName);
        btnSave        = view.findViewById(R.id.btnSaveProfile);
        btnCancel      = view.findViewById(R.id.btnCancelProfile);
        loadingOverlay = view.findViewById(R.id.loadingOverlay);

        populateCurrentData();
        setupListeners();
    }

    // ── Precarga de datos actuales ─────────────────────────────────────────────

    private void populateCurrentData() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        // Nombre actual
        String displayName = user.getDisplayName();
        if (displayName != null && !displayName.isEmpty()) {
            etDisplayName.setText(displayName);
            etDisplayName.setSelection(displayName.length());
        }

        // Foto: local primero, luego Google
        File localPhoto = getLocalPhotoFile();
        if (localPhoto != null && localPhoto.exists()) {
            loadPhotoFromFile(localPhoto);
        } else {
            Uri googlePhoto = user.getPhotoUrl();
            if (googlePhoto != null) {
                loadPhotoFromUri(googlePhoto);
            } else {
                ivProfilePhoto.setImageResource(R.drawable.ic_person_24);
            }
        }

        btnSave.setEnabled(false);
    }

    // ── Listeners ──────────────────────────────────────────────────────────────

    private void setupListeners() {
        // Ambos abren la galería: el texto "Cambiar foto" y la propia foto
        btnChangePhoto.setOnClickListener(v -> openGallery());
        ivProfilePhoto.setOnClickListener(v -> openGallery());

        etDisplayName.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int i, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                tilDisplayName.setError(null);
                updateSaveButtonState(s);
            }
        });

        btnSave.setOnClickListener(v -> onSave());
        btnCancel.setOnClickListener(v -> dismiss());
    }

    private void updateSaveButtonState(CharSequence currentText) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            btnSave.setEnabled(false);
            return;
        }

        String current = currentText != null ? currentText.toString().trim() : "";
        String saved   = user.getDisplayName() != null ? user.getDisplayName() : "";

        boolean nameChanged  = !current.equals(saved);
        boolean photoChanged = copiedPhotoFile != null;

        btnSave.setEnabled((nameChanged || photoChanged) && !current.isEmpty());
    }

    // ── Galería ────────────────────────────────────────────────────────────────

    /**
     * Abre el selector de imágenes del sistema.
     * GetContent("image/*") es la forma correcta y moderna —
     * compatible con Google Fotos, Files, y cualquier proveedor.
     */
    private void openGallery() {
        galleryLauncher.launch("image/*");
    }

    // ── Guardar ────────────────────────────────────────────────────────────────

    private void onSave() {
        String newName = etDisplayName.getText() != null
                ? etDisplayName.getText().toString().trim() : "";

        if (newName.isEmpty()) {
            tilDisplayName.setError(getString(R.string.profile_error_name_required));
            return;
        }
        if (newName.length() > 40) {
            tilDisplayName.setError(getString(R.string.profile_error_name_too_long));
            return;
        }

        setLoading(true);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            setLoading(false);
            Toast.makeText(requireContext(),
                    getString(R.string.profile_error_no_session), Toast.LENGTH_SHORT).show();
            dismiss();
            return;
        }

        // Guardamos la ruta local ANTES de llamar a Firebase
        if (copiedPhotoFile != null) {
            requireContext()
                    .getSharedPreferences(PREFS_PROFILE, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_PHOTO_PATH, copiedPhotoFile.getAbsolutePath())
                    .apply();
        }

        // Actualizamos solo el displayName en Firebase Auth
        UserProfileChangeRequest request = new UserProfileChangeRequest.Builder()
                .setDisplayName(newName)
                .build();

        user.updateProfile(request)
                .addOnSuccessListener(unused -> {
                    setLoading(false);
                    // Invalidar caché de Glide para que la foto nueva se muestre inmediatamente
                    if (copiedPhotoFile != null) {
                        Glide.get(requireContext()).clearMemory();
                    }
                    Toast.makeText(requireContext(),
                            getString(R.string.profile_saved), Toast.LENGTH_SHORT).show();
                    if (listener != null) {
                        listener.onProfileUpdated(newName, copiedPhotoFile);
                    }
                    dismiss();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(requireContext(),
                            getString(R.string.profile_error_save), Toast.LENGTH_LONG).show();
                });
    }

    // ── Helpers de foto ────────────────────────────────────────────────────────

    /**
     * Copia el contenido del URI a filesDir/profile_photo.jpg.
     * El archivo interno es permanente y no necesita permisos de galería.
     * Se llama inmediatamente al recibir el URI, antes de que caduque.
     */
    private File copyUriToInternal(Uri uri) {
        try {
            File dest = new File(requireContext().getFilesDir(), "profile_photo.jpg");
            InputStream  in  = requireContext().getContentResolver().openInputStream(uri);
            if (in == null) return null;
            OutputStream out = new FileOutputStream(dest);
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
            in.close();
            out.close();
            return dest;
        } catch (Exception e) {
            return null;
        }
    }

    private File getLocalPhotoFile() {
        String path = requireContext()
                .getSharedPreferences(PREFS_PROFILE, Context.MODE_PRIVATE)
                .getString(KEY_PHOTO_PATH, null);
        if (path == null) return null;
        File f = new File(path);
        return f.exists() ? f : null;
    }

    private void loadPhotoFromFile(File file) {
        if (!isAdded() || getContext() == null) return;
        ivProfilePhoto.clearColorFilter();
        Glide.with(requireContext())
                .load(file)
                .transform(new CircleCrop())
                .placeholder(R.drawable.ic_person_24)
                .error(R.drawable.ic_person_24)
                .into(ivProfilePhoto);
    }

    private void loadPhotoFromUri(Uri uri) {
        if (!isAdded() || getContext() == null) return;
        ivProfilePhoto.clearColorFilter();
        Glide.with(requireContext())
                .load(uri)
                .transform(new CircleCrop())
                .placeholder(R.drawable.ic_person_24)
                .error(R.drawable.ic_person_24)
                .into(ivProfilePhoto);
    }

    private void setLoading(boolean loading) {
        loadingOverlay.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSave.setEnabled(!loading);
        btnCancel.setEnabled(!loading);
        btnChangePhoto.setEnabled(!loading);
        etDisplayName.setEnabled(!loading);
    }
}