package com.example.clef;

import org.junit.Test;
import static org.junit.Assert.*;

import com.example.clef.utils.PasswordGenerator;

/**
 * Pruebas unitarias del generador de contraseñas.
 *
 * El generador usa SecureRandom, que es criptográficamente seguro.
 * Estas pruebas validan que:
 *  - Las contraseñas generadas cumplen las reglas configuradas.
 *  - La aleatoriedad es real (no siempre devuelve lo mismo).
 *  - Los límites de longitud se respetan.
 *
 * Ubicación: app/src/test/java/com/example/clef/utils/
 */
public class PasswordGeneratorTest {

    // ════════════════════════════════════════════════════════════════════════
    // 1. Longitud
    // ════════════════════════════════════════════════════════════════════════

    @Test
    public void generate_longitudCorrecta() {
        for (int len : new int[]{8, 16, 24, 32, 64}) {
            String pwd = PasswordGenerator.generate(len, true, true, true, true);
            assertEquals("La contraseña debe tener exactamente " + len + " caracteres",
                    len, pwd.length());
        }
    }

    @Test
    public void generate_longitudMinima4Aplicada() {
        // Aunque se pidan menos de 4, debe generar al menos 4
        String pwd = PasswordGenerator.generate(2, true, true, true, true);
        assertTrue("La longitud mínima es 4", pwd.length() >= 4);
    }

    // ════════════════════════════════════════════════════════════════════════
    // 2. Garantía de tipos de caracteres
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Si se activan mayúsculas, la contraseña DEBE contener al menos una.
     * Requisito RF-23: "al menos un carácter de cada tipo activo".
     */
    @Test
    public void generate_conMayusculasContieneAlMenosUna() {
        for (int i = 0; i < 20; i++) {
            String pwd = PasswordGenerator.generate(12, true, false, false, false);
            assertTrue("Con mayúsculas activadas debe haber al menos una: " + pwd,
                    pwd.chars().anyMatch(Character::isUpperCase));
        }
    }

    @Test
    public void generate_conMinusculasContieneAlMenosUna() {
        for (int i = 0; i < 20; i++) {
            String pwd = PasswordGenerator.generate(12, false, true, false, false);
            assertTrue("Con minúsculas activadas debe haber al menos una: " + pwd,
                    pwd.chars().anyMatch(Character::isLowerCase));
        }
    }

    @Test
    public void generate_conNumerosContieneAlMenosUno() {
        for (int i = 0; i < 20; i++) {
            String pwd = PasswordGenerator.generate(12, false, false, true, false);
            assertTrue("Con números activados debe haber al menos uno: " + pwd,
                    pwd.chars().anyMatch(Character::isDigit));
        }
    }

    @Test
    public void generate_conSimbolosContieneAlMenosUno() {
        String simbolos = "!@#$%^&*()_+-=[]{}|;:,.<>?";
        for (int i = 0; i < 20; i++) {
            String pwd = PasswordGenerator.generate(12, false, false, false, true);
            boolean tieneSimbolos = pwd.chars()
                    .anyMatch(c -> simbolos.indexOf(c) >= 0);
            assertTrue("Con símbolos activados debe haber al menos uno: " + pwd,
                    tieneSimbolos);
        }
    }

    /**
     * Con todos los tipos activos, la contraseña debe contener uno de cada.
     */
    @Test
    public void generate_todosTiposActivos_contieneUnoDeEach() {
        String simbolos = "!@#$%^&*()_+-=[]{}|;:,.<>?";
        for (int i = 0; i < 30; i++) {
            String pwd = PasswordGenerator.generate(16, true, true, true, true);
            assertTrue("Debe tener mayúscula: " + pwd,
                    pwd.chars().anyMatch(Character::isUpperCase));
            assertTrue("Debe tener minúscula: " + pwd,
                    pwd.chars().anyMatch(Character::isLowerCase));
            assertTrue("Debe tener número: " + pwd,
                    pwd.chars().anyMatch(Character::isDigit));
            assertTrue("Debe tener símbolo: " + pwd,
                    pwd.chars().anyMatch(c -> simbolos.indexOf(c) >= 0));
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 3. Restricciones de caracteres
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Si solo mayúsculas están activas, no debe haber minúsculas ni números.
     */
    @Test
    public void generate_soloMayusculas_noContieneOtrosTipos() {
        for (int i = 0; i < 20; i++) {
            String pwd = PasswordGenerator.generate(16, true, false, false, false);
            assertFalse("Sin minúsculas, no debe haber minúsculas: " + pwd,
                    pwd.chars().anyMatch(Character::isLowerCase));
            assertFalse("Sin números, no debe haber números: " + pwd,
                    pwd.chars().anyMatch(Character::isDigit));
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 4. Aleatoriedad
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Dos contraseñas generadas con los mismos parámetros deben ser distintas.
     * SecureRandom garantiza que no hay patrones predecibles.
     */
    @Test
    public void generate_dosGeneracionesDiferentes() {
        String pwd1 = PasswordGenerator.generate(16, true, true, true, true);
        String pwd2 = PasswordGenerator.generate(16, true, true, true, true);
        assertNotEquals("Dos contraseñas generadas deben ser diferentes", pwd1, pwd2);
    }

    /**
     * El fallback (sin ningún tipo activo) no debe lanzar excepción
     * y debe devolver minúsculas (el fallback del código).
     */
    @Test
    public void generate_sinNingunTipoActivoNoFalla() {
        // El código tiene un fallback a lowercase si todo está desactivado
        String pwd = PasswordGenerator.generate(12, false, false, false, false);
        assertNotNull("No debe lanzar excepción", pwd);
        assertTrue("El fallback debe generar al menos algo", pwd.length() >= 4);
    }
}