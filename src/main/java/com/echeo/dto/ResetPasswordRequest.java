package com.echeo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Complète le flux "mot de passe oublié" : consomme le token reçu par email
 * et applique le nouveau mot de passe. Endpoint non explicitement demandé
 * dans le prompt mais nécessaire pour que le flux soit utilisable de bout en bout.
 */
public class ResetPasswordRequest {

    @NotBlank(message = "Le token de réinitialisation est obligatoire.")
    private String token;

    @NotBlank(message = "Le nouveau mot de passe est obligatoire.")
    @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères.")
    private String newPassword;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
