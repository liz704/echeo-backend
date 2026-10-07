package com.echeo.model.entity;

import com.echeo.model.enums.Role;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Utilisateur de la plateforme ÉCHÉO.
 * Peut être propriétaire de groupes, membre de groupes, et créateur de rappels personnels.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "phone", length = 30)
    private String phone;

    // Ne doit jamais être sérialisé en JSON dans les réponses API.
    @JsonIgnore
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role = Role.USER;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** Langue des e-mails : "fr" (défaut) ou "en". */
    @Column(name = "preferred_locale", nullable = false, length = 5)
    private String preferredLocale = "fr";

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "email_verification_token")
    private UUID emailVerificationToken;

    @Column(name = "email_verification_sent_at")
    private OffsetDateTime emailVerificationSentAt;

    @Column(name = "week_plan_enabled", nullable = false)
    private boolean weekPlanEnabled = false;

    /** 1 = Monday … 7 = Sunday (ISO). */
    @Column(name = "week_plan_day", nullable = false)
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private int weekPlanDay = 1;

    @Column(name = "week_plan_send_time", nullable = false)
    private LocalTime weekPlanSendTime = LocalTime.of(8, 0);

    @Column(name = "week_plan_token")
    private UUID weekPlanToken;

    @Column(name = "week_plan_last_sent_at")
    private OffsetDateTime weekPlanLastSentAt;



    public User() {
    }

    public User(Long id, String fullName, String email, String phone, String passwordHash,
                Role role, OffsetDateTime createdAt) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.role = role;
        this.createdAt = createdAt;
    }

    // Valeur par défaut posée avant l'insertion si non fournie par l'appelant.
    @jakarta.persistence.PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
        if (this.role == null) {
            this.role = Role.USER;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getPreferredLocale() {
        return preferredLocale == null || preferredLocale.isBlank() ? "fr" : preferredLocale;
    }

    public void setPreferredLocale(String preferredLocale) {
        this.preferredLocale = preferredLocale;
    }

    public boolean isWeekPlanEnabled() { return weekPlanEnabled; }
    public void setWeekPlanEnabled(boolean weekPlanEnabled) { this.weekPlanEnabled = weekPlanEnabled; }
    public int getWeekPlanDay() { return weekPlanDay; }
    public void setWeekPlanDay(int weekPlanDay) { this.weekPlanDay = weekPlanDay; }
    public LocalTime getWeekPlanSendTime() { return weekPlanSendTime; }
    public void setWeekPlanSendTime(LocalTime weekPlanSendTime) { this.weekPlanSendTime = weekPlanSendTime; }
    public UUID getWeekPlanToken() { return weekPlanToken; }
    public void setWeekPlanToken(UUID weekPlanToken) { this.weekPlanToken = weekPlanToken; }
    public OffsetDateTime getWeekPlanLastSentAt() { return weekPlanLastSentAt; }
    public void setWeekPlanLastSentAt(OffsetDateTime weekPlanLastSentAt) { this.weekPlanLastSentAt = weekPlanLastSentAt; }

    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }
    public UUID getEmailVerificationToken() { return emailVerificationToken; }
    public void setEmailVerificationToken(UUID emailVerificationToken) { this.emailVerificationToken = emailVerificationToken; }
    public OffsetDateTime getEmailVerificationSentAt() { return emailVerificationSentAt; }
    public void setEmailVerificationSentAt(OffsetDateTime emailVerificationSentAt) { this.emailVerificationSentAt = emailVerificationSentAt; }
}
