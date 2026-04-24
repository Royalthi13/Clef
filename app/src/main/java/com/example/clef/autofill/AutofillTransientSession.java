package com.example.clef.autofill;

import com.example.clef.data.model.Credential;

/**
 * Sesión efímera post-biometría para evitar pedir huella varias veces en
 * flujos multi-pantalla (ej: Amazon/OpenAI — email primero, password
 * después, en pantallas distintas).
 *
 * Vive en memoria del proceso del servicio de autofill. Si el proceso muere
 * (Android lo mata a voluntad), se pierde y se vuelve a pedir biometría,
 * lo cual es aceptable desde el punto de vista de seguridad.
 *
 * Timeout corto (30 s) porque:
 *   - Flujos de login multi-pantalla normalmente son 5-10 s.
 *   - Más tiempo empezaría a ser un riesgo si el usuario olvida el móvil
 *     desbloqueado con Clef de autofill activo en algún form.
 */
final class AutofillTransientSession {

    private static final long TIMEOUT_MS = 30_000L;

    private static final Object LOCK = new Object();
    private static Credential credential;
    private static long expiresAt;

    private AutofillTransientSession() {}

    /** Guarda la credencial seleccionada tras biometría exitosa. */
    static void store(Credential c) {
        synchronized (LOCK) {
            credential = c;
            expiresAt = System.currentTimeMillis() + TIMEOUT_MS;
        }
    }

    /** Devuelve la credencial vigente o null si no hay o expiró. */
    static Credential get() {
        synchronized (LOCK) {
            if (credential == null) return null;
            if (System.currentTimeMillis() > expiresAt) {
                clear();
                return null;
            }
            return credential;
        }
    }

    /** Invalida la sesión. */
    static void clear() {
        synchronized (LOCK) {
            credential = null;
            expiresAt = 0L;
        }
    }
}
