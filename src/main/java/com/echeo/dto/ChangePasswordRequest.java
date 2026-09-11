package com.echeo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ChangePasswordRequest {

    @NotBlank(message = "Le mot de passe actuel est obligatoire.")
    private String currentPassword;

    @NotBlank(message = "Le nouveau mot de passe est obligatoire.")
    @Size(min = 8, message = "Le nouveau mot de passe doit contenir au moins 8 caractères.")
    private String newPassword;

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
