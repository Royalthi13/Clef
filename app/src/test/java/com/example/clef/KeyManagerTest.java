package com.example.clef;

import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;

import com.example.clef.crypto.KeyManager;
import com.example.clef.data.model.Credential;
import com.example.clef.data.model.Vault;

import java.util.Arrays;

/**
 * Pruebas unitarias de KeyManager.
 *
 * KeyManager orquesta la arquitectura de tres claves (Caja A, Caja B, DEK).
 * Estas pruebas validan que los flujos de registro, login y recuperación
 * con PUK funcionan correctamente y que los errores se detectan.
 *
 * Ubicación: app/src/test/java/com/example/clef/crypto/
 */
public class KeyManagerTest {

    private static final String PASSWORD_MAESTRA  = "MiContrasena#Segura1";
    private static final String PASSWORD_MAESTRA2 = "OtraContrasena#Segura2";
    private static final String PASSWORD_MAL      = "ContrasenaMal#99";

    private KeyManager km;

    @Before
    public void setUp() {
        km = new KeyManager();
    }

    // ════════════════════════════════════════════════════════════════════════
    // 1. register() — Registro completo
    // ════════════════════════════════════════════════════════════════════════

    /**
     * El registro debe devolver todos los campos necesarios para subir a Firebase.
     * Si falta cualquiera, el backend rechazará los datos.
     */
    @Test
    public void register_devuelveTodasLasCajas() throws Exception {
        KeyManager.RegistrationBundle bundle = km.register(PASSWORD_MAESTRA.toCharArray());

        assertNotNull("saltBase64 no debe ser null",       bundle.saltBase64);
        assertNotNull("cajaABase64 no debe ser null",      bundle.cajaABase64);
        assertNotNull("cajaBBase64 no debe ser null",      bundle.cajaBBase64);
        assertNotNull("bovedaCifradaBase64 no debe ser null", bundle.bovedaCifradaBase64);
        assertNotNull("puk no debe ser null",              bundle.puk);
        assertNotNull("dek no debe ser null",              bundle.dek);

        assertFalse("saltBase64 no puede estar vacío",       bundle.saltBase64.isEmpty());
        assertFalse("cajaABase64 no puede estar vacío",      bundle.cajaABase64.isEmpty());
        assertFalse("cajaBBase64 no puede estar vacío",      bundle.cajaBBase64.isEmpty());
        assertFalse("bovedaCifradaBase64 no puede estar vacío", bundle.bovedaCifradaBase64.isEmpty());
        assertFalse("puk no puede estar vacío",              bundle.puk.isEmpty());
    }

    /**
     * La DEK debe tener exactamente 32 bytes (256 bits).
     * Es la clave que cifra todo el vault: debe ser AES-256.
     */
    @Test
    public void register_dekTiene32Bytes() throws Exception {
        KeyManager.RegistrationBundle bundle = km.register(PASSWORD_MAESTRA.toCharArray());
        assertEquals("La DEK debe tener 32 bytes", 32, bundle.dek.length);
    }

    /**
     * El PUK debe tener el formato correcto: 8 grupos de 4 hex separados por '-'.
     * Formato esperado: XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX (32 hex + 7 guiones)
     */
    @Test
    public void register_pukFormatoCorrecto() throws Exception {
        KeyManager.RegistrationBundle bundle = km.register(PASSWORD_MAESTRA.toCharArray());
        String puk = bundle.puk;

        // Debe tener exactamente 8 grupos de 4 caracteres separados por '-'
        String[] partes = puk.split("-");
        assertEquals("El PUK debe tener 8 grupos", 8, partes.length);
        for (String parte : partes) {
            assertEquals("Cada grupo del PUK debe tener 4 caracteres", 4, parte.length());
            assertTrue("Cada grupo debe ser hexadecimal",
                    parte.matches("[0-9A-F]{4}"));
        }
    }

    /**
     * Dos registros deben producir DEKs distintas (aleatoriedad).
     * Si la DEK fuera siempre la misma, todos los vaults serían idénticos.
     */
    @Test
    public void register_dosRegistrosDEKsDistintas() throws Exception {
        KeyManager.RegistrationBundle b1 = km.register(PASSWORD_MAESTRA.toCharArray());
        KeyManager.RegistrationBundle b2 = km.register(PASSWORD_MAESTRA.toCharArray());
        assertFalse("Dos registros deben generar DEKs distintas",
                Arrays.equals(b1.dek, b2.dek));
    }

    // ════════════════════════════════════════════════════════════════════════
    // 2. login() — Desbloqueo con contraseña maestra
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Login con contraseña correcta debe recuperar la misma DEK del registro.
     * Esta es la propiedad fundamental: la misma contraseña siempre abre
     * el mismo vault.
     */
    @Test
    public void login_contrasenaCorrectaRecuperaDEK() throws Exception {
        KeyManager.RegistrationBundle bundle = km.register(PASSWORD_MAESTRA.toCharArray());

        KeyManager.LoginResult result = km.login(
                PASSWORD_MAESTRA.toCharArray(),
                bundle.saltBase64,
                bundle.cajaABase64,
                bundle.bovedaCifradaBase64
        );

        assertArrayEquals("El login debe recuperar la misma DEK que el registro",
                bundle.dek, result.dek);
    }

    /**
     * Login con contraseña correcta debe devolver un vault no nulo.
     */
    @Test
    public void login_contrasenaCorrectaDevuelveVault() throws Exception {
        KeyManager.RegistrationBundle bundle = km.register(PASSWORD_MAESTRA.toCharArray());

        KeyManager.LoginResult result = km.login(
                PASSWORD_MAESTRA.toCharArray(),
                bundle.saltBase64,
                bundle.cajaABase64,
                bundle.bovedaCifradaBase64
        );

        assertNotNull("El vault descifrado no debe ser null", result.vault);
        assertNotNull("La lista de credenciales no debe ser null",
                result.vault.getCredentials());
    }

    /**
     * Login con contraseña INCORRECTA debe lanzar excepción.
     * Esta es la protección central: una contraseña mala no abre el vault.
     * AES-GCM detecta la clave incorrecta a través del tag de autenticación.
     */
    @Test
    public void login_contrasenaIncorrectaLanzaExcepcion() throws Exception {
        KeyManager.RegistrationBundle bundle = km.register(PASSWORD_MAESTRA.toCharArray());

        try {
            km.login(
                    PASSWORD_MAL.toCharArray(),
                    bundle.saltBase64,
                    bundle.cajaABase64,
                    bundle.bovedaCifradaBase64
            );
            fail("El login con contraseña incorrecta debe lanzar excepción");
        } catch (Exception e) {
            // Correcto: AEADBadTagException envuelta en Exception
            // La contraseña incorrecta produce una KEK incorrecta,
            // que falla al descifrar la Caja A.
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 3. cifrarVault() y descifrarVault()
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Cifrar y descifrar un vault con credenciales debe conservar los datos.
     * Esto valida que las credenciales del usuario no se corrompen
     * en el ciclo guardar → cargar.
     */
    @Test
    public void cifrarDescifrarVault_conservaDatos() throws Exception {
        KeyManager.RegistrationBundle bundle = km.register(PASSWORD_MAESTRA.toCharArray());
        byte[] dek = bundle.dek;

        // Crear un vault con una credencial
        Vault vaultOriginal = new Vault();
        Credential credencial = new Credential(
                "Gmail",
                "usuario@gmail.com",
                "MiPassword#123",
                "https://gmail.com",
                "Cuenta principal",
                Credential.Category.WORK
        );
        vaultOriginal.addCredential(credencial);

        // Cifrar
        String cifrado = km.cifrarVault(vaultOriginal, dek);
        assertNotNull("El vault cifrado no debe ser null", cifrado);
        assertFalse("El vault cifrado no debe estar vacío", cifrado.isEmpty());

        // Descifrar
        Vault vaultRecuperado = km.descifrarVault(cifrado, dek);
        assertNotNull("El vault descifrado no debe ser null", vaultRecuperado);
        assertEquals("El número de credenciales debe conservarse",
                1, vaultRecuperado.getCredentials().size());

        Credential credRecuperada = vaultRecuperado.getCredentials().get(0);
        assertEquals("El título debe conservarse",    "Gmail",             credRecuperada.getTitle());
        assertEquals("El usuario debe conservarse",   "usuario@gmail.com", credRecuperada.getUsername());
        assertEquals("La password debe conservarse",  "MiPassword#123",    credRecuperada.getPassword());
        assertEquals("La URL debe conservarse",       "https://gmail.com", credRecuperada.getUrl());
        assertEquals("Las notas deben conservarse",   "Cuenta principal",  credRecuperada.getNotes());
    }

    /**
     * El vault cifrado debe ser diferente en cada llamada (IV aleatorio).
     */
    @Test
    public void cifrarVault_mismoDatoProduceCifradosDiferentes() throws Exception {
        KeyManager.RegistrationBundle bundle = km.register(PASSWORD_MAESTRA.toCharArray());
        Vault vault = new Vault();

        String cifrado1 = km.cifrarVault(vault, bundle.dek);
        String cifrado2 = km.cifrarVault(vault, bundle.dek);
        assertNotEquals("Cada cifrado del vault debe ser diferente (IV aleatorio)",
                cifrado1, cifrado2);
    }

    // ════════════════════════════════════════════════════════════════════════
    // 4. recoverWithPuk() — Recuperación de acceso
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Recuperación con PUK correcto debe devolver la misma DEK.
     * Esta es la garantía de que el usuario puede recuperar su vault
     * aunque haya olvidado la contraseña maestra.
     */
    @Test
    public void recoverWithPuk_pukCorrectoRecuperaDEK() throws Exception {
        KeyManager.RegistrationBundle bundle = km.register(PASSWORD_MAESTRA.toCharArray());

        KeyManager.RecoveryResult result = km.recoverWithPuk(
                bundle.puk.toCharArray(),
                PASSWORD_MAESTRA2.toCharArray(), // nueva contraseña
                bundle.saltBase64,
                bundle.cajaBBase64,
                bundle.bovedaCifradaBase64
        );

        assertArrayEquals("La recuperación con PUK debe devolver la misma DEK que el registro",
                bundle.dek, result.dek);
    }

    /**
     * Tras la recuperación con PUK, se debe generar una nueva Caja A
     * que funcione con la nueva contraseña maestra.
     */
    @Test
    public void recoverWithPuk_nuevaCajaAFuncionaConNuevaContrasena() throws Exception {
        KeyManager.RegistrationBundle bundle = km.register(PASSWORD_MAESTRA.toCharArray());

        // Recuperar con PUK y nueva contraseña
        KeyManager.RecoveryResult recovery = km.recoverWithPuk(
                bundle.puk.toCharArray(),
                PASSWORD_MAESTRA2.toCharArray(),
                bundle.saltBase64,
                bundle.cajaBBase64,
                bundle.bovedaCifradaBase64
        );

        // La nueva Caja A debe abrirse con la nueva contraseña
        KeyManager.LoginResult loginConNuevaContrasena = km.login(
                PASSWORD_MAESTRA2.toCharArray(),
                bundle.saltBase64,
                recovery.nuevaCajaABase64,
                bundle.bovedaCifradaBase64
        );

        assertArrayEquals("La nueva Caja A debe funcionar con la nueva contraseña",
                bundle.dek, loginConNuevaContrasena.dek);
    }

    /**
     * La contraseña maestra ANTERIOR no debe abrir la nueva Caja A.
     * Tras la recuperación, el acceso con la contraseña vieja queda bloqueado.
     */
    @Test
    public void recoverWithPuk_contrasenaViejaNoAbreNuevaCajaA() throws Exception {
        KeyManager.RegistrationBundle bundle = km.register(PASSWORD_MAESTRA.toCharArray());

        KeyManager.RecoveryResult recovery = km.recoverWithPuk(
                bundle.puk.toCharArray(),
                PASSWORD_MAESTRA2.toCharArray(),
                bundle.saltBase64,
                bundle.cajaBBase64,
                bundle.bovedaCifradaBase64
        );

        try {
            km.login(
                    PASSWORD_MAESTRA.toCharArray(), // contraseña VIEJA
                    bundle.saltBase64,
                    recovery.nuevaCajaABase64,      // nueva Caja A
                    bundle.bovedaCifradaBase64
            );
            fail("La contraseña antigua no debe abrir la nueva Caja A tras la recuperación");
        } catch (Exception e) {
            // Correcto: la Caja A antigua ya no sirve
        }
    }

    /**
     * PUK incorrecto debe lanzar excepción.
     * Un atacante que intente adivinar el PUK no debe poder abrir el vault.
     */
    @Test
    public void recoverWithPuk_pukIncorrectoLanzaExcepcion() throws Exception {
        KeyManager.RegistrationBundle bundle = km.register(PASSWORD_MAESTRA.toCharArray());
        String pukMalo = "AAAA-BBBB-CCCC-DDDD-EEEE-FFFF-0000-1111"; // PUK inventado

        try {
            km.recoverWithPuk(
                    pukMalo.toCharArray(),
                    PASSWORD_MAESTRA2.toCharArray(),
                    bundle.saltBase64,
                    bundle.cajaBBase64,
                    bundle.bovedaCifradaBase64
            );
            fail("Un PUK incorrecto debe lanzar excepción");
        } catch (Exception e) {
            // Correcto: AEADBadTagException por PUK incorrecto
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 5. generarNuevoCajaB() — Invalidación del PUK tras recuperación
    // ════════════════════════════════════════════════════════════════════════

    /**
     * El nuevo PUK generado tras la recuperación debe poder abrir la nueva Caja B.
     * Esto valida que el usuario recibe un PUK funcional.
     */
    @Test
    public void generarNuevoCajaB_nuevoPukFunciona() throws Exception {
        KeyManager.RegistrationBundle bundle = km.register(PASSWORD_MAESTRA.toCharArray());

        // Generar nueva Caja B con nuevo PUK
        KeyManager.NuevoPukBundle nuevoPuk = km.generarNuevoCajaB(bundle.dek, bundle.saltBase64);
        assertNotNull("La nueva Caja B no debe ser null", nuevoPuk.cajaBBase64);
        assertNotNull("El nuevo PUK no debe ser null", nuevoPuk.puk);
        assertFalse("El nuevo PUK no debe estar vacío", nuevoPuk.puk.isEmpty());

        // El nuevo PUK debe funcionar para recuperar la DEK
        KeyManager.RecoveryResult resultado = km.recoverWithPuk(
                nuevoPuk.puk.toCharArray(),
                PASSWORD_MAESTRA2.toCharArray(),
                bundle.saltBase64,
                nuevoPuk.cajaBBase64,
                bundle.bovedaCifradaBase64
        );

        assertArrayEquals("El nuevo PUK debe recuperar la misma DEK",
                bundle.dek, resultado.dek);
    }

    /**
     * El PUK VIEJO no debe abrir la nueva Caja B.
     * Esto garantiza la invalidación criptográfica del PUK antiguo.
     */
    @Test
    public void generarNuevoCajaB_pukViejoNoAbreNuevaCajaB() throws Exception {
        KeyManager.RegistrationBundle bundle = km.register(PASSWORD_MAESTRA.toCharArray());
        String pukViejo = bundle.puk;

        // Generar nueva Caja B (invalida el PUK viejo)
        KeyManager.NuevoPukBundle nuevoPuk = km.generarNuevoCajaB(bundle.dek, bundle.saltBase64);

        try {
            km.recoverWithPuk(
                    pukViejo.toCharArray(),         // PUK VIEJO
                    PASSWORD_MAESTRA2.toCharArray(),
                    bundle.saltBase64,
                    nuevoPuk.cajaBBase64,           // nueva Caja B
                    bundle.bovedaCifradaBase64
            );
            fail("El PUK viejo no debe abrir la nueva Caja B (invalidación criptográfica)");
        } catch (Exception e) {
            // Correcto: el PUK viejo ya no funciona porque la Caja B fue reemplazada
        }
    }

    /**
     * El nuevo PUK debe ser diferente del anterior (no reutilización).
     */
    @Test
    public void generarNuevoCajaB_nuevoPukDiferenteDelAnterior() throws Exception {
        KeyManager.RegistrationBundle bundle = km.register(PASSWORD_MAESTRA.toCharArray());
        KeyManager.NuevoPukBundle nuevoPuk = km.generarNuevoCajaB(bundle.dek, bundle.saltBase64);

        assertNotEquals("El nuevo PUK debe ser diferente del original",
                bundle.puk, nuevoPuk.puk);
    }
}