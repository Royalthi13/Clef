package com.example.clef.ui.settings;

import android.Manifest;
import com.google.firebase.functions.FirebaseFunctions;
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
import com.example.clef.utils.SecurePrefs;
import android.content.res.Configuration;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
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
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProfileEditDialog extends BottomSheetDialogFragment {

    public static final String PREFS_NAME = "profile_prefs";

    public static String photoPathKey(String uid) { return "local_photo_path_" + uid; }
    public static String photoSigKey(String uid)  { return "photo_signature_" + uid; }

    public interface OnProfileUpdatedListener {
        void onProfileUpdated(String newName, File localPhoto);
    }
    private OnProfileUpdatedListener listener;

    private ImageView         ivProfilePhoto;
    private TextInputLayout   tilDisplayName;
    private TextInputEditText etDisplayName;
    private MaterialButton    btnSaveProfile;
    private MaterialButton    btnCancelProfile;
    private View              loadingOverlay;
    private View              btnChangePhoto;

    private File selectedPhotoFile = null;
    private Uri  cameraUri         = null;

    private com.example.clef.data.remote.AuthManager authManager;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // ── Launchers ──────────────────────────────────────────────────────────────

    private final ActivityResultLauncher<Intent> reAuthLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;
                authManager.handleSignInResult(result.getData(), (user, error) -> {
                    if (error != null || user == null) {
                        if (!isAdded()) return;
                        Toast.makeText(requireContext(),
                                "Autenticación fallida. Inténtalo de nuevo.", Toast.LENGTH_SHORT).show();
                        setLoading(false);
                        return;
                    }
                    // Reautenticación con Google completada — ahora sí borrar
                    com.google.android.gms.auth.api.signin.GoogleSignInAccount account =
                            com.google.android.gms.auth.api.signin.GoogleSignIn
                                    .getLastSignedInAccount(requireContext());
                    if (account == null) { setLoading(false); return; }
                    authManager.reauthenticateWithGoogle(account.getIdToken(), (u, e) -> {
                        if (e != null) {
                            if (!isAdded()) return;
                            setLoading(false);
                            Toast.makeText(requireContext(),
                                    getString(R.string.delete_account_error), Toast.LENGTH_LONG).show();
                            return;
                        }
                        proceedDeleteAccount();
                    });
                });
            });

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
                else if (isAdded()) Toast.makeText(requireContext(),
                        "Se necesita permiso de cámara", Toast.LENGTH_SHORT).show();
            });

    public static ProfileEditDialog newInstance() { return new ProfileEditDialog(); }
    public void setOnProfileUpdatedListener(OnProfileUpdatedListener l) { this.listener = l; }

    @Override
    public void onStart() {
        super.onStart();
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
            if (dialog != null) {
                BottomSheetBehavior<android.widget.FrameLayout> behavior = dialog.getBehavior();
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        }
    }

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

        authManager = new com.example.clef.data.remote.AuthManager(
                requireActivity(), getString(R.string.default_web_client_id));

        btnChangePhoto.setOnClickListener(v -> showPhotoOptions());
        ivProfilePhoto .setOnClickListener(v -> showPhotoOptions());
        btnSaveProfile .setOnClickListener(v -> onSave());
        btnCancelProfile.setOnClickListener(v -> dismiss());

        view.findViewById(R.id.btnDeleteAccount).setOnClickListener(v -> confirmDeleteAccount());
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

        SharedPreferences prefs = SecurePrefs.get(requireContext(), PREFS_NAME);
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
        if (!isAdded()) return;
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Cambiar foto de perfil")
                .setItems(new String[]{"Cámara", "Galería"}, (d, which) -> {
                    if (which == 0) requestCameraPermission();
                    else openGallery();
                })
                .show();
    }

    private void requestCameraPermission() {
        if (!isAdded()) return;
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            launchCamera();
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void launchCamera() {
        if (!isAdded()) return;
        try {
            File tmp = File.createTempFile("cam_", ".jpg",
                    requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES));
            cameraUri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".provider", tmp);
            cameraLauncher.launch(cameraUri);
        } catch (IOException e) {
            if (isAdded()) Toast.makeText(requireContext(),
                    "Error al abrir la cámara", Toast.LENGTH_SHORT).show();
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
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

                if (!isAdded()) return; // FIX: guardia antes de tocar la UI
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
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(),
                            "No se pudo cargar la imagen", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    // ── Eliminar cuenta ────────────────────────────────────────────────────────

    private void confirmDeleteAccount() {
        if (!isAdded()) return;
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.delete_account_confirm_title))
                .setMessage(getString(R.string.delete_account_confirm_message))
                .setPositiveButton(getString(R.string.delete_account_confirm_action), (d, w) -> verifyIdentity())
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void verifyIdentity() {
        if (!isAdded()) return;
        setLoading(true);
        showPasswordDialog();
    }

    private void showPasswordDialog() {
        if (!isAdded()) return;
        android.view.View dialogView = android.view.LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_verify_password, null);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Confirma tu identidad")
                .setView(dialogView)
                .setPositiveButton("Confirmar", (d, w) -> {
                    com.google.android.material.textfield.TextInputEditText et =
                            dialogView.findViewById(R.id.etVerifyPassword);
                    Editable editable = et.getText();
                    char[] pwd = new char[editable != null ? editable.length() : 0];
                    if (editable != null) editable.getChars(0, editable.length(), pwd, 0);
                    if (pwd.length == 0) { setLoading(false); return; }
                    verifyPassword(pwd);
                })
                .setNegativeButton(getString(R.string.cancel), (d, w) -> setLoading(false))
                .show();
    }

    private void verifyPassword(char[] password) {
        if (!isAdded()) return;
        com.example.clef.data.repository.VaultRepository repo =
                new com.example.clef.data.repository.VaultRepository(requireContext());
        com.example.clef.data.remote.FirebaseManager.UserData data = repo.loadOfflineUserData();
        if (data == null) { setLoading(false); return; }

        executor.execute(() -> {
            try {
                new com.example.clef.crypto.KeyManager()
                        .login(password, data.salt, data.cajaA, data.vault);
            } catch (Exception e) {
                Arrays.fill(password, '\0');
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    setLoading(false);
                    Toast.makeText(requireContext(),
                            "Contraseña incorrecta", Toast.LENGTH_SHORT).show();
                });
                return;
            }

            // Contraseña local verificada — reautenticar en Firebase según proveedor
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                Arrays.fill(password, '\0');
                requireActivity().runOnUiThread(() -> setLoading(false));
                return;
            }

            boolean isEmailUser = user.getProviderData().stream()
                    .anyMatch(p -> "password".equals(p.getProviderId()));

            Arrays.fill(password, '\0');
            if (isEmailUser) {
                requireActivity().runOnUiThread(() -> { if (isAdded()) proceedDeleteAccount(); });
            } else {
                if (isAdded()) silentReauthAndDelete();
            }
        });
    }

    private void silentReauthAndDelete() {
        if (!isAdded()) return;
        authManager.silentReauthenticate((user, error) -> {
            if (error == null && user != null) {
                proceedDeleteAccount();
            } else {
                // Fallback: mostrar selector de Google
                if (isAdded()) reAuthLauncher.launch(authManager.getGoogleSignInIntent());
            }
        });
    }

    private void deleteAccount() {
        if (!isAdded()) return;
        setLoading(true);
        reAuthLauncher.launch(authManager.getGoogleSignInIntent());
    }

    private void proceedDeleteAccount() {
        if (!isAdded()) return;
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) { setLoading(false); return; }

        android.util.Log.d("DeleteAccount", "Refrescando token...");
        currentUser.getIdToken(true)
                .addOnSuccessListener(tokenResult -> {
                    android.util.Log.d("DeleteAccount", "Token OK, llamando vía HTTP...");
                    executor.execute(() -> callDeleteAccountHttp(tokenResult.getToken()));
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("DeleteAccount", "Token refresh FAILED", e);
                    if (!isAdded()) return;
                    setLoading(false);
                    Toast.makeText(requireContext(),
                            getString(R.string.delete_account_error), Toast.LENGTH_LONG).show();
                });
    }

    private void callDeleteAccountHttp(String idToken) {
        android.app.Activity activity = getActivity();
        if (activity == null) return;
        try {
            java.net.URL url = new java.net.URL(
                    "https://us-central1-clef-efe86.cloudfunctions.net/deleteAccountHttp");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + idToken);
            conn.setDoOutput(true);
            conn.getOutputStream().write("{\"data\":{}}".getBytes());

            int code = conn.getResponseCode();
            android.util.Log.d("DeleteAccount", "HTTP response: " + code);

            if (code == 200) {
                activity.runOnUiThread(() -> {
                    com.example.clef.data.repository.VaultRepository repo =
                            new com.example.clef.data.repository.VaultRepository(activity);
                    repo.clearLocalVault();
                    repo.clearKeyCache();
                    com.example.clef.utils.SessionManager.getInstance().setOnLockListener(null);
                    com.example.clef.utils.SessionManager.getInstance().lock();
                    FirebaseAuth.getInstance().signOut();
                    Toast.makeText(activity,
                            getString(R.string.delete_account_success), Toast.LENGTH_SHORT).show();
                    activity.startActivity(
                            new android.content.Intent(activity,
                                    com.example.clef.ui.auth.LoginActivity.class)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK |
                                            android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK));
                });
            } else {
                android.util.Log.e("DeleteAccount", "HTTP error: " + code);
                activity.runOnUiThread(() -> {
                    setLoading(false);
                    Toast.makeText(activity,
                            getString(R.string.delete_account_error), Toast.LENGTH_LONG).show();
                });
            }
        } catch (Exception e) {
            android.util.Log.e("DeleteAccount", "HTTP call FAILED", e);
            activity.runOnUiThread(() -> {
                setLoading(false);
                Toast.makeText(activity,
                        getString(R.string.delete_account_error), Toast.LENGTH_LONG).show();
            });
        }
    }

    // ── Guardar ────────────────────────────────────────────────────────────────

    private void onSave() {
        if (!isAdded()) return;

        String newName = etDisplayName.getText() != null
                ? etDisplayName.getText().toString().trim() : "";
        if (newName.length() < 2) {
            tilDisplayName.setError("El nombre debe tener al menos 2 caracteres");
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { dismiss(); return; }

        setLoading(true);

        // FIX: Borrar foto antigua antes de guardar la nueva para evitar fuga de datos
        deleteOldPhotoIfNeeded(user.getUid());

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
                    if (!isAdded()) return; // FIX: guardia — el diálogo puede haberse cerrado
                    setLoading(false);
                    Toast.makeText(requireContext(), "Perfil actualizado", Toast.LENGTH_SHORT).show();
                    if (listener != null) listener.onProfileUpdated(newName, selectedPhotoFile);
                    dismiss();
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return; // FIX: guardia
                    setLoading(false);
                    Toast.makeText(requireContext(),
                            "Guardado localmente. Sin conexión con el servidor.",
                            Toast.LENGTH_LONG).show();
                    if (listener != null) listener.onProfileUpdated(newName, selectedPhotoFile);
                    dismiss();
                });
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * FIX: Borra el fichero de foto anterior del usuario si existe y es distinto
     * del que se va a guardar. Evita que avatares_<uid>.jpg antiguos se acumulen.
     */
    private void deleteOldPhotoIfNeeded(String uid) {
        if (!isAdded()) return;
        SharedPreferences prefs = SecurePrefs.get(requireContext(), PREFS_NAME);
        String oldPath = prefs.getString(photoPathKey(uid), null);
        if (oldPath != null && selectedPhotoFile != null
                && !oldPath.equals(selectedPhotoFile.getAbsolutePath())) {
            File old = new File(oldPath);
            if (old.exists()) {
                //noinspection ResultOfMethodCallIgnored
                old.delete();
            }
        }
    }

    private File getProfilePhotoFile() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        String uid = (u != null) ? u.getUid() : "anon";
        File dir = new File(requireContext().getFilesDir(), "profile");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, "avatar_" + uid + ".jpg");
    }

    private void setLoading(boolean loading) {
        if (!isAdded()) return;
        if (loadingOverlay != null)
            loadingOverlay.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSaveProfile  .setEnabled(!loading);
        btnCancelProfile.setEnabled(!loading);
    }
}