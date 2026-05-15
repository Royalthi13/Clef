package com.example.clef;

import org.junit.Test;
import static org.junit.Assert.*;

import com.example.clef.utils.PasswordGenerator;
import com.example.clef.utils.PasswordStrengthHelper;

/**
 * Pruebas unitarias de PasswordStrengthHelper.
 *
 * Valida que el evaluador de fortaleza clasifica correctamente las contraseñas.
 * Esta clase no tiene dependencias de Android (no usa Context para evaluate()),
 * por lo que puede probarse en JVM pura.
 *
 * Ubicación: app/src/test/java/com/example/clef/utils/
 */
public class PasswordStrengthHelperTest {

    // ════════════════════════════════════════════════════════════════════════
    // 1. Contraseñas DÉBILES
    // ════════════════════════════════════════════════════════════════════════

    @Test
    public void evaluate_nullEsDebil() {
        assertEquals(PasswordStrengthHelper.Strength.WEAK,
                PasswordStrengthHelper.evaluate(null));
    }

    @Test
    public void evaluate_vacioeEsDebil() {
        assertEquals(PasswordStrengthHelper.Strength.WEAK,
                PasswordStrengthHelper.evaluate(""));
    }

    @Test
    public void evaluate_soloLetrasMinusculasCortoEsDebil() {
        // "abc" → longitud 3, un solo tipo → WEAK
        assertEquals(PasswordStrengthHelper.Strength.WEAK,
                PasswordStrengthHelper.evaluate("abc"));
    }

    @Test
    public void evaluate_soloNumeros6digitsEsDebil() {
        // "123456" → 6 chars, un tipo → score ≤ 3 → WEAK
        assertEquals(PasswordStrengthHelper.Strength.WEAK,
                PasswordStrengthHelper.evaluate("123456"));
    }

    @Test
    public void evaluate_contrasenaTipica123EsDebil() {
        // "password" → 8 chars, un tipo → WEAK
        assertEquals(PasswordStrengthHelper.Strength.WEAK,
                PasswordStrengthHelper.evaluate("password"));
    }

    // ════════════════════════════════════════════════════════════════════════
    // 2. Contraseñas MEDIAS
    // ════════════════════════════════════════════════════════════════════════

    @Test
    public void evaluate_8CharsLetrasYNumeroEsMedio() {
        // "Password1" → 9 chars, upper+lower+digit = 3 tipos, score 5 → MEDIUM
        assertEquals(PasswordStrengthHelper.Strength.MEDIUM,
                PasswordStrengthHelper.evaluate("Password1"));
    }

    @Test
    public void evaluate_10CharsDostiposEsMedio() {

        assertEquals(PasswordStrengthHelper.Strength.MEDIUM,
                PasswordStrengthHelper.evaluate("Abcdefgh12"));
    }

    // ════════════════════════════════════════════════════════════════════════
    // 3. Contraseñas FUERTES
    // ════════════════════════════════════════════════════════════════════════

    @Test
    public void evaluate_16CharsTodosTiposEsFuerte() {
        // "MyP@ssw0rd#2024!" → 16+ chars, 4 tipos → score 8 → STRONG
        assertEquals(PasswordStrengthHelper.Strength.STRONG,
                PasswordStrengthHelper.evaluate("MyP@ssw0rd#2024!"));
    }

    @Test
    public void evaluate_24CharsVariedadEsFuerte() {
        assertEquals(PasswordStrengthHelper.Strength.STRONG,
                PasswordStrengthHelper.evaluate("Tr0ub4dor&3HorseStaple!Z"));
    }

    @Test
    public void evaluate_generadorDefaultEsFuerte() {
        // Una contraseña generada por defecto (16 chars, upper+lower+digit)
        // debe ser al menos MEDIUM, idealmente STRONG
        String generada = PasswordGenerator.generate(16, true, true, true, false);
        PasswordStrengthHelper.Strength strength = PasswordStrengthHelper.evaluate(generada);
        assertTrue("Una contraseña de 16 chars con 3 tipos debe ser MEDIUM o STRONG",
                strength == PasswordStrengthHelper.Strength.MEDIUM
                        || strength == PasswordStrengthHelper.Strength.STRONG);
    }

    // ════════════════════════════════════════════════════════════════════════
    // 4. Casos borde
    // ════════════════════════════════════════════════════════════════════════

    @Test
    public void evaluate_espaciosEnBlancoCuentanComoCars() {
        // " " solo → muy corto → WEAK
        assertEquals(PasswordStrengthHelper.Strength.WEAK,
                PasswordStrengthHelper.evaluate("   "));
    }

    @Test
    public void evaluate_caracteresUnicodeNoRompen() {
        // No debe lanzar excepción con chars unicode
        try {
            PasswordStrengthHelper.evaluate("contraseña123!ABC");
        } catch (Exception e) {
            fail("No debe lanzar excepción con caracteres unicode: " + e.getMessage());
        }
    }
}