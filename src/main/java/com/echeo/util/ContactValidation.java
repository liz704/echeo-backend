package com.echeo.util;

import com.echeo.exception.InvalidArgumentException;

import java.util.regex.Pattern;

/**
 * Validation des contacts.
 * - Téléphone : format international E.164 (ex. +237612345678, +33612345678).
 * - Email : format RFC simple (existence réelle = lien de confirmation).
 */
public final class ContactValidation {

    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{7,14}$");
    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private ContactValidation() {
    }

    public static String normalizeAndValidatePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String cleaned = phone.trim().replaceAll("[\\s().-]", "");
        if (cleaned.startsWith("00")) {
            cleaned = "+" + cleaned.substring(2);
        }
        if (!E164.matcher(cleaned).matches()) {
            throw new InvalidArgumentException(
                    "Numéro invalide. Utilise le format international avec indicatif, ex. +237612345678 ou +33612345678.");
        }
        return cleaned;
    }

    public static void validateEmailFormat(String email) {
        if (email == null || email.isBlank() || !EMAIL.matcher(email.trim()).matches()) {
            throw new InvalidArgumentException("Format d'email invalide.");
        }
    }
}
