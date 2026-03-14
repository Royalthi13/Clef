package com.example.clef.data.remote;

// AuthManager gestiona la autenticación del usuario con Google + Firebase Auth.
// Separa la responsabilidad de auth de FirebaseManager (que solo toca Firestore).
// Flujo: Google Sign-In → credencial Google → Firebase Auth → FirebaseUser disponible.

import android.app.Activity;
import android.content.Intent;

import androidx.activity.result.ActivityResultLauncher;

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

public class AuthManager {

    private final FirebaseAuth auth;
    private final GoogleSignInClient googleSignInClient;

    public AuthManager(Activity activity, String webClientId) {
        this.auth = FirebaseAuth.getInstance();

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build();

        this.googleSignInClient = GoogleSignIn.getClient(activity, gso);
    }

    // Devuelve el Intent que lanza el selector de cuentas de Google
    public Intent getGoogleSignInIntent() {
        return googleSignInClient.getSignInIntent();
    }

    // Procesa el resultado del selector de cuentas y autentica en Firebase
    // Llama al callback con el FirebaseUser si va bien, o null si falla
    public void handleSignInResult(Intent data, AuthCallback callback) {
        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            firebaseAuthWithGoogle(account.getIdToken(), callback);
        } catch (ApiException e) {
            callback.onResult(null, e);
        }
    }

    // Intercambia el token de Google por una sesión de Firebase
    private void firebaseAuthWithGoogle(String idToken, AuthCallback callback) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(credential)
                .addOnSuccessListener(result -> callback.onResult(result.getUser(), null))
                .addOnFailureListener(e -> callback.onResult(null, e));
    }

    // Cierra sesión en Firebase y en Google (para que vuelva a pedir cuenta)
    public void signOut(Activity activity, Runnable onComplete) {
        auth.signOut();
        googleSignInClient.signOut()
                .addOnCompleteListener(activity, t -> onComplete.run());
    }

    // Devuelve el usuario actualmente autenticado, o null si no hay sesión
    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }

    public interface AuthCallback {
        void onResult(FirebaseUser user, Exception error);
    }
}
