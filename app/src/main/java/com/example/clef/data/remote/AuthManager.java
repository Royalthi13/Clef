package com.example.clef.data.remote;

import android.app.Activity;
import android.content.Intent;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

/**
 * Gestiona la autenticación del usuario mediante Google Sign-In y Firebase Auth.
 * Soporta registro e inicio de sesión con Google y con correo/contraseña.
 */
public class AuthManager {

    private final FirebaseAuth       auth;
    private final GoogleSignInClient googleSignInClient;

    /**
     * @param activity    Activity desde la que se lanzará el selector de cuentas de Google.
     * @param webClientId ID del cliente web generado por google-services (R.string.default_web_client_id).
     */
    public AuthManager(Activity activity, String webClientId) {
        this.auth = FirebaseAuth.getInstance();

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build();

        this.googleSignInClient = GoogleSignIn.getClient(activity, gso);
    }

    /** @return Intent que abre el selector de cuentas de Google. */
    public Intent getGoogleSignInIntent() {
        return googleSignInClient.getSignInIntent();
    }

    /**
     * Procesa el resultado del selector de Google y autentica al usuario en Firebase.
     *
     * @param data     Intent devuelto por el selector de cuentas de Google.
     * @param callback resultado con el FirebaseUser o el error.
     */
    public void handleSignInResult(Intent data, AuthCallback callback) {
        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            firebaseAuthWithGoogle(account.getIdToken(), callback);
        } catch (ApiException e) {
            callback.onResult(null, e);
        }
    }

    /**
     * Cierra la sesión en Firebase y en Google para que el selector de cuentas
     * vuelva a mostrarse en el próximo inicio de sesión.
     *
     * @param activity   Activity actual, requerida por el cliente de Google.
     * @param onComplete acción ejecutada al completar el cierre de sesión.
     */
    public void signOut(Activity activity, Runnable onComplete) {
        auth.signOut();
        googleSignInClient.signOut()
                .addOnCompleteListener(activity, t -> onComplete.run());
    }

    /**
     * Inicia sesión con correo y contraseña.
     *
     * @param email    correo del usuario.
     * @param password contraseña del usuario.
     * @param callback resultado con el FirebaseUser o el error.
     */
    public void signInWithEmail(String email, String password, AuthCallback callback) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> callback.onResult(result.getUser(), null))
                .addOnFailureListener(e -> callback.onResult(null, e));
    }

    /**
     * Registra un nuevo usuario con correo y contraseña.
     *
     * @param email    correo del nuevo usuario.
     * @param password contraseña del nuevo usuario.
     * @param callback resultado con el FirebaseUser o el error.
     */
    public void registerWithEmail(String email, String password, AuthCallback callback) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> callback.onResult(result.getUser(), null))
                .addOnFailureListener(e -> callback.onResult(null, e));
    }

    /**
     * Envía un correo de verificación al usuario autenticado actualmente.
     *
     * @param callback resultado con null si se envió correctamente, o el error si falló.
     */
    public void sendEmailVerification(AuthCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onResult(null, new Exception("No hay usuario autenticado"));
            return;
        }
        user.sendEmailVerification()
                .addOnSuccessListener(v -> callback.onResult(null, null))
                .addOnFailureListener(e -> callback.onResult(null, e));
    }

    /** @return true si el usuario actual tiene el correo verificado. */
    public boolean isEmailVerified() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null && user.isEmailVerified();
    }

    /**
     * Envía un correo de recuperación de contraseña a la dirección indicada.
     *
     * @param email    correo del usuario.
     * @param callback resultado con null si se envió correctamente, o el error si falló.
     */
    public void sendPasswordReset(String email, AuthCallback callback) {
        auth.sendPasswordResetEmail(email)
                .addOnSuccessListener(v -> callback.onResult(null, null))
                .addOnFailureListener(e -> callback.onResult(null, e));
    }

    /** @return FirebaseUser con sesión activa, o null si no hay sesión. */
    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }

    /**
     * Reautentica al usuario en Firebase usando la cuenta Google en caché,
     * sin mostrar el selector de cuentas. Útil antes de operaciones sensibles.
     *
     * @param callback resultado con el FirebaseUser o el error.
     */
    public void silentReauthenticate(AuthCallback callback) {
        googleSignInClient.silentSignIn()
                .addOnSuccessListener(account ->
                        reauthenticateWithGoogle(account.getIdToken(), callback))
                .addOnFailureListener(e -> callback.onResult(null, e));
    }

    /**
     * Reautentica al usuario en Firebase con un idToken de Google.
     *
     * @param idToken  token de identidad obtenido de Google Sign-In.
     * @param callback resultado con el FirebaseUser o el error.
     */
    public void reauthenticateWithGoogle(String idToken, AuthCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) { callback.onResult(null, new Exception("no_user")); return; }
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        user.reauthenticate(credential)
                .addOnSuccessListener(unused -> callback.onResult(user, null))
                .addOnFailureListener(e -> callback.onResult(null, e));
    }

    /**
     * Intercambia el idToken de Google por una credencial Firebase e inicia sesión.
     *
     * @param idToken  token de identidad obtenido de Google Sign-In.
     * @param callback resultado con el FirebaseUser o el error.
     */
    private void firebaseAuthWithGoogle(String idToken, AuthCallback callback) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(credential)
                .addOnSuccessListener(result -> callback.onResult(result.getUser(), null))
                .addOnFailureListener(e -> callback.onResult(null, e));
    }

    /** Callback para recibir el resultado de las operaciones de autenticación. */
    public interface AuthCallback {
        /**
         * @param user  FirebaseUser si la operación tuvo éxito, null si falló.
         * @param error excepción si la operación falló, null si tuvo éxito.
         */
        void onResult(FirebaseUser user, Exception error);
    }
}
