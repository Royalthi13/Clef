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
 * Esta clase gestiona el inicio de sesión con Google y Firebase.
 *
 * El proceso de login tiene 3 pasos:
 *   1. La app abre el selector de cuentas de Google (con getGoogleSignInIntent).
 *   2. El usuario elige su cuenta de Google.
 *   3. Google le da a la app un "token" que usamos para entrar en Firebase
 *      (con handleSignInResult). Firebase nos devuelve el FirebaseUser.
 *
 * Esta clase está separada de FirebaseManager porque tiene una responsabilidad
 * diferente: FirebaseManager gestiona los DATOS, AuthManager gestiona la IDENTIDAD.
 */
public class AuthManager {

    private final FirebaseAuth       auth;
    private final GoogleSignInClient googleSignInClient;

    /**
     * Crea el AuthManager y lo configura para pedir el token de Google.
     *
     * @param activity    La pantalla desde la que se va a abrir el selector de Google.
     * @param webClientId El ID del cliente web que genera automáticamente el plugin
     *                    google-services. En el código se usa como R.string.default_web_client_id.
     */
    public AuthManager(Activity activity, String webClientId) {
        this.auth = FirebaseAuth.getInstance();

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build();

        this.googleSignInClient = GoogleSignIn.getClient(activity, gso);
    }

    /**
     * Devuelve el Intent que abre el selector de cuentas de Google.
     * La Activity lo lanza con ActivityResultLauncher y espera a que el
     * usuario elija su cuenta.
     *
     * @return Intent listo para abrir el selector de Google.
     */
    public Intent getGoogleSignInIntent() {
        return googleSignInClient.getSignInIntent();
    }

    /**
     * Procesa la cuenta que eligió el usuario en el selector de Google
     * y la usa para autenticarse en Firebase.
     *
     * Hay que llamar a este método desde el ActivityResultLauncher de la Activity,
     * pasándole el Intent que devuelve el selector de Google.
     *
     * @param data     El Intent que devuelve el selector de Google con la cuenta elegida.
     * @param callback Se llama al terminar: con el FirebaseUser si todo fue bien,
     *                 o con null y el error si algo falló.
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
     * Cierra la sesión en Firebase y también en Google.
     * Si no cerramos en Google, la próxima vez entraría solo sin preguntar la cuenta.
     * Cerrando los dos, vuelve a aparecer el selector de cuentas.
     *
     * @param activity   La Activity actual, necesaria para el cierre en Google.
     * @param onComplete Código que se ejecuta cuando el cierre de sesión ha terminado.
     */
    public void signOut(Activity activity, Runnable onComplete) {
        auth.signOut();
        googleSignInClient.signOut()
                .addOnCompleteListener(activity, t -> onComplete.run());
    }

    /**
     * Inicia sesión con correo y contraseña en Firebase.
     *
     * @param email    Correo del usuario.
     * @param password Contraseña del usuario.
     * @param callback Resultado con el FirebaseUser o el error.
     */
    public void signInWithEmail(String email, String password, AuthCallback callback) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> callback.onResult(result.getUser(), null))
                .addOnFailureListener(e -> callback.onResult(null, e));
    }

    /**
     * Registra un nuevo usuario con correo y contraseña en Firebase.
     *
     * @param email    Correo del nuevo usuario.
     * @param password Contraseña del nuevo usuario.
     * @param callback Resultado con el FirebaseUser o el error.
     */
    public void registerWithEmail(String email, String password, AuthCallback callback) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> callback.onResult(result.getUser(), null))
                .addOnFailureListener(e -> callback.onResult(null, e));
    }

    /**
     * Comprueba si hay algún usuario con sesión iniciada en Firebase.
     *
     * @return El FirebaseUser si hay sesión activa, o null si no hay nadie logueado.
     */
    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }

    /**
     * Intercambia el token de Google por una sesión en Firebase.
     * Firebase verifica que el token es válido y crea la sesión del usuario.
     *
     * @param idToken  El token que nos dio Google al elegir la cuenta.
     * @param callback Se llama con el FirebaseUser si tuvo éxito, o con el error si falló.
     */
    private void firebaseAuthWithGoogle(String idToken, AuthCallback callback) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(credential)
                .addOnSuccessListener(result -> callback.onResult(result.getUser(), null))
                .addOnFailureListener(e -> callback.onResult(null, e));
    }

    /**
     * Interfaz que define cómo recibir el resultado del login con Google.
     * Quien llame a handleSignInResult tiene que implementar esta interfaz.
     */
    public interface AuthCallback {
        /**
         * Se llama cuando el proceso de login termina, bien o mal.
         *
         * @param user  El usuario de Firebase si el login fue bien, o null si falló.
         * @param error El error que ocurrió si el login falló, o null si fue bien.
         */
        void onResult(FirebaseUser user, Exception error);
    }
}
