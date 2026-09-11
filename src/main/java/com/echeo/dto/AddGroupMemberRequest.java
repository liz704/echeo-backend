package com.echeo.dto;

import jakarta.validation.constraints.Email;

/**
 * Ajout d'un membre à un groupe. Deux façons de remplir ce DTO, jamais
 * mélangées :
 * - Membre inscrit : renseigner userId uniquement.
 * - Membre externe (cas normal, sans compte ÉCHÉO) : renseigner fullName
 *   et email (phone optionnel), laisser userId à null.
 * La validation métier (l'un OU l'autre, jamais les deux/aucun) est faite
 * dans GroupService, pas ici, pour pouvoir renvoyer un message clair.
 */
public class AddGroupMemberRequest {

    private Long userId;

    private String fullName;

    @Email(message = "Format d'email invalide.")
    private String email;

    private String phone;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
