package com.echeo.dto;

import jakarta.validation.constraints.Email;

/**
 * Mise à jour des infos de contact d'un membre (surtout externe).
 * Pour un membre inscrit, seul le téléphone externe optionnel peut
 * être ajusté côté groupe ; nom/email viennent du compte User.
 */
public class UpdateGroupMemberRequest {

    private String fullName;

    @Email(message = "Format d'email invalide.")
    private String email;

    private String phone;

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
