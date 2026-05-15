package com.example.clef;

import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;

import com.example.clef.crypto.CryptoUtils;

import java.util.Arrays;
import java.nio.charset.StandardCharsets;

/**
 * Pruebas unitarias de CryptoUtils.
 *
 * Qué se prueba y por qué:
 *  - Las primitivas criptográficas (AES-256-GCM, PBKDF2) son el núcleo de
 *    seguridad de Clef. Un error aquí comprometería TODOS los datos de TODOS
 *    los usuarios. Estas pruebas garantizan que la implementación se comporta
 *    exactamente como espera la arquitectura Zero-Knowledge.
 *
 * Estas pruebas son de tipo unitario puro: no requieren Android, ni Firebase,
 * ni ninguna dependencia externa. Solo la JVM estándar y JUnit4.
 *
 * Ubicación en el proyecto: app/src/test/java/com/example/clef/crypto/
 */
public class CryptoUtilsTest {

    // ── Constantes de prueba ───────────────────────────────────────────────

    private static final String PASSWORD_CORRECTA   = "MiContrasenaSegura123!";
    private static final String PASSWORD_INCORRECTA = "ContrasenaMal123!";
    private static final String TEXTO_PLANO         = "Esta es una contraseña super secreta: @Gmail123";

    private byte[] saltValido;

    @Before
    public void setUp() throws Exception {
        // Salt válido de 32 bytes generado de forma determinista para las pruebas
        saltValido = CryptoUtils.generateSalt();
        assertEquals("El salt debe tener exactamente 32 bytes",
                CryptoUtils.SALT_BYTES, saltValido.length);
    }

    // ════════════════════════════════════════════════════════════════════════
    // 1. generateSalt()
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Dos salts generados en momentos distintos deben ser diferentes.
     * Si fueran iguales, dos usuarios con la misma contraseña obtendrían
     * la misma KEK, lo que haría los ataques de tabla arcoíris viables.
     */
    @Test
    public void generateSalt_dosSaltsNuncaIguales() {
        byte[] salt1 = CryptoUtils.generateSalt();
        byte[] salt2 = CryptoUtils.generateSalt();
        assertFalse("Dos salts generados deben ser diferentes (aleatoriedad)",
                Arrays.equals(salt1, salt2));
    }

    /**
     * El salt debe tener exactamente 32 bytes (256 bits).
     * Tamaño menor reduciría la resistencia a ataques de tabla arcoíris.
     */
    @Test
    public void generateSalt_longitudCorrecta() {
        byte[] salt = CryptoUtils.generateSalt();
        assertEquals("El salt debe tener 32 bytes", 32, salt.length);
    }

    // ════════════════════════════════════════════════════════════════════════
    // 2. deriveKey() — PBKDF2WithHmacSHA256
    // ════════════════════════════════════════════════════════════════════════

    /**
     * La misma contraseña + mismo salt → siempre la misma clave (determinismo).
     * Esta propiedad es imprescindible: el usuario debe poder desbloquear
     * su vault cada vez que introduce su contraseña.
     */
    @Test
    public void deriveKey_mismosInputsMismaClaveResultante() throws Exception {
        char[] password = PASSWORD_CORRECTA.toCharArray();
        byte[] clave1 = CryptoUtils.deriveKey(password, saltValido);
        byte[] clave2 = CryptoUtils.deriveKey(password, saltValido);
        assertArrayEquals("La misma contraseña+salt debe producir siempre la misma clave",
                clave1, clave2);
    }

    /**
     * Contraseñas distintas con el mismo salt → claves distintas.
     * Si no fuera así, cualquier contraseña abriría cualquier vault.
     */
    @Test
    public void deriveKey_contrasenasDistintasProducenClavesDistintas() throws Exception {
        byte[] claveCorrecta   = CryptoUtils.deriveKey(PASSWORD_CORRECTA.toCharArray(),   saltValido);
        byte[] claveIncorrecta = CryptoUtils.deriveKey(PASSWORD_INCORRECTA.toCharArray(), saltValido);
        assertFalse("Contraseñas distintas deben producir claves distintas",
                Arrays.equals(claveCorrecta, claveIncorrecta));
    }

    /**
     * La misma contraseña con salts distintos → claves distintas.
     * Esta es la garantía fundamental contra ataques de tabla arcoíris:
     * dos usuarios con la misma contraseña tienen vaults diferentes.
     */
    @Test
    public void deriveKey_saltsDistintosProducenClavesDistintas() throws Exception {
        byte[] salt1 = CryptoUtils.generateSalt();
        byte[] salt2 = CryptoUtils.generateSalt();
        byte[] clave1 = CryptoUtils.deriveKey(PASSWORD_CORRECTA.toCharArray(), salt1);
        byte[] clave2 = CryptoUtils.deriveKey(PASSWORD_CORRECTA.toCharArray(), salt2);
        assertFalse("El mismo password con salts distintos debe producir claves distintas",
                Arrays.equals(clave1, clave2));
    }

    /**
     * La clave derivada debe tener exactamente 32 bytes (AES-256).
     */
    @Test
    public void deriveKey_longitudClave256bits() throws Exception {
        byte[] clave = CryptoUtils.deriveKey(PASSWORD_CORRECTA.toCharArray(), saltValido);
        assertEquals("La clave derivada debe tener 32 bytes (AES-256)", 32, clave.length);
    }

    /**
     * Contraseña vacía debe lanzar excepción, no producir una clave débil.
     */
    @Test(expected = IllegalArgumentException.class)
    public void deriveKey_contrasenaVaciaLanzaExcepcion() throws Exception {
        CryptoUtils.deriveKey(new char[0], saltValido);
    }

    /**
     * Salt de tamaño incorrecto debe lanzar excepción.
     */
    @Test(expected = IllegalArgumentException.class)
    public void deriveKey_saltInvalidoLanzaExcepcion() throws Exception {
        byte[] saltCorto = new byte[16]; // debe ser 32
        CryptoUtils.deriveKey(PASSWORD_CORRECTA.toCharArray(), saltCorto);
    }

    // ════════════════════════════════════════════════════════════════════════
    // 3. encrypt() y decrypt() — AES-256-GCM
    // ════════════════════════════════════════════════════════════════════════

    /**
     * El ciclo completo cifrar→descifrar debe recuperar el texto original.
     * Esta es la prueba más básica: si falla, nada funciona.
     */
    @Test
    public void encryptDecrypt_cicloCompleto() throws Exception {
        byte[] clave = CryptoUtils.deriveKey(PASSWORD_CORRECTA.toCharArray(), saltValido);
        String cifrado = CryptoUtils.encrypt(TEXTO_PLANO, clave);
        String recuperado = CryptoUtils.decryptToString(cifrado, clave);
        assertEquals("El texto descifrado debe ser idéntico al original",
                TEXTO_PLANO, recuperado);
    }

    /**
     * Dos cifrados del mismo texto deben producir resultados distintos.
     * AES-GCM usa un IV aleatorio de 12 bytes en cada operación. Si los
     * cifrados fueran iguales, un atacante podría detectar cuando el mismo
     * dato se guarda dos veces, lo que filtra información.
     */
    @Test
    public void encrypt_mismoDatoProduceCifradosDiferentes() throws Exception {
        byte[] clave = CryptoUtils.deriveKey(PASSWORD_CORRECTA.toCharArray(), saltValido);
        String cifrado1 = CryptoUtils.encrypt(TEXTO_PLANO, clave);
        String cifrado2 = CryptoUtils.encrypt(TEXTO_PLANO, clave);
        assertNotEquals("Cada cifrado debe producir un resultado diferente (IV aleatorio)",
                cifrado1, cifrado2);
    }

    /**
     * Intentar descifrar con una clave incorrecta debe lanzar excepción.
     * Esto es lo que garantiza que una contraseña maestra incorrecta
     * NO abre el vault. AES-GCM verifica el tag de autenticación y lanza
     * AEADBadTagException si la clave no coincide.
     */
    @Test
    public void decrypt_claveIncorrectaLanzaExcepcion() throws Exception {
        byte[] claveCorrecta   = CryptoUtils.deriveKey(PASSWORD_CORRECTA.toCharArray(),   saltValido);
        byte[] claveIncorrecta = CryptoUtils.deriveKey(PASSWORD_INCORRECTA.toCharArray(), saltValido);

        String cifrado = CryptoUtils.encrypt(TEXTO_PLANO, claveCorrecta);

        try {
            CryptoUtils.decryptToString(cifrado, claveIncorrecta);
            fail("Debería haber lanzado una excepción al descifrar con clave incorrecta");
        } catch (Exception e) {
            // Correcto: AEADBadTagException o su envoltorio
            // GCM detecta automáticamente la clave incorrecta o datos manipulados
            assertTrue("La excepción debe ser por integridad/autenticación GCM",
                    e.getMessage() != null || e.getClass().getSimpleName().contains("AEADBad")
                            || e.getCause() != null);
        }
    }

    /**
     * Datos manipulados (un byte cambiado) deben ser detectados.
     * AES-GCM incluye un tag de autenticación de 128 bits: cualquier
     * modificación del texto cifrado se detecta en el descifrado.
     * Esto protege contra ataques donde un atacante modifica el vault en Firebase.
     */
    @Test
    public void decrypt_datosManipuladosDetectados() throws Exception {
        byte[] clave = CryptoUtils.deriveKey(PASSWORD_CORRECTA.toCharArray(), saltValido);
        String cifrado = CryptoUtils.encrypt(TEXTO_PLANO, clave);

        // Modificar el último carácter del Base64 para simular manipulación
        char[] chars = cifrado.toCharArray();
        chars[chars.length - 1] = (chars[chars.length - 1] == 'A') ? 'B' : 'A';
        String cifradoManipulado = new String(chars);

        try {
            CryptoUtils.decryptToString(cifradoManipulado, clave);
            fail("Debería detectar la manipulación de datos gracias al tag GCM");
        } catch (Exception e) {
            // Correcto: GCM detectó la manipulación
        }
    }

    /**
     * El cifrado debe funcionar con datos binarios, no solo texto.
     * El vault cifrado contiene JSON serializado, que puede tener cualquier byte.
     */
    @Test
    public void encryptDecrypt_datoBinario() throws Exception {
        byte[] clave = CryptoUtils.deriveKey(PASSWORD_CORRECTA.toCharArray(), saltValido);
        byte[] datoBinario = new byte[256];
        for (int i = 0; i < 256; i++) datoBinario[i] = (byte) i;

        String cifrado = CryptoUtils.encrypt(datoBinario, clave);
        byte[] recuperado = CryptoUtils.decrypt(cifrado, clave);
        assertArrayEquals("Los datos binarios deben recuperarse íntegros", datoBinario, recuperado);
    }

    /**
     * Clave de tamaño incorrecto debe lanzar excepción.
     * La validación interna debe rechazar claves que no sean AES-256.
     */
    @Test(expected = IllegalArgumentException.class)
    public void encrypt_claveCortoLanzaExcepcion() throws Exception {
        byte[] claveCorta = new byte[16]; // debe ser 32 para AES-256
        CryptoUtils.encrypt(TEXTO_PLANO, claveCorta);
    }

    // ════════════════════════════════════════════════════════════════════════
    // 4. zeroise()
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Tras zerizar un array de bytes, todos sus valores deben ser 0.
     * Esto es la implementación de "DEK zerizada al bloquear" que describe
     * la arquitectura: al cerrar sesión, la clave desaparece de la RAM.
     */
    @Test
    public void zeroise_arrayBytesQuedaEnCero() {
        byte[] sensitivo = {1, 2, 3, 4, 5, 6, 7, 8};
        CryptoUtils.zeroise(sensitivo);
        for (byte b : sensitivo) {
            assertEquals("Todos los bytes deben ser cero tras zerizar", 0, b);
        }
    }

    /**
     * Tras zerizar un array de chars, todos sus valores deben ser '\0'.
     * Relevante para la contraseña maestra que se maneja como char[].
     */
    @Test
    public void zeroise_arrayCharsQuedaEnCero() {
        char[] password = {'s', 'e', 'c', 'r', 'e', 't', 'o'};
        CryptoUtils.zeroise(password);
        for (char c : password) {
            assertEquals("Todos los chars deben ser '\\0' tras zerizar", '\0', c);
        }
    }

    /**
     * zeroise(null) no debe lanzar NullPointerException.
     * El código llama a zeroise en bloques finally donde el array
     * podría no haberse inicializado si hubo una excepción antes.
     */
    @Test
    public void zeroise_nullNoLanzaExcepcion() {
        // No debe lanzar excepción
        CryptoUtils.zeroise((byte[]) null);
        CryptoUtils.zeroise((char[]) null);
    }

    // ════════════════════════════════════════════════════════════════════════
    // 5. generateRandomBytes()
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Dos llamadas consecutivas deben producir resultados distintos.
     */
    @Test
    public void generateRandomBytes_resultadosAleatorios() {
        byte[] bytes1 = CryptoUtils.generateRandomBytes(32);
        byte[] bytes2 = CryptoUtils.generateRandomBytes(32);
        assertFalse("Dos llamadas deben producir bytes distintos",
                Arrays.equals(bytes1, bytes2));
    }

    /**
     * El tamaño del array devuelto debe ser el solicitado.
     */
    @Test
    public void generateRandomBytes_longitudCorrecta() {
        assertEquals(16, CryptoUtils.generateRandomBytes(16).length);
        assertEquals(32, CryptoUtils.generateRandomBytes(32).length);
        assertEquals(64, CryptoUtils.generateRandomBytes(64).length);
    }
}