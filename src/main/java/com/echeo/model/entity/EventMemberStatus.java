package com.echeo.model.entity;

import com.echeo.model.enums.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Statut de paiement d'un membre de groupe pour un événement donné :
 * montant requis, montant déjà payé, et statut dérivé
 * (PENDING / PARTIALLY_PAID / PAID / SURPLUS / OVERDUE).
 * Pivot central du suivi des paiements partiels, complets et des surplus.
 *
 * Référence GroupMember (et non plus User directement) : le membre concerné
 * peut ne pas avoir de compte ÉCHÉO (voir GroupMember.getContact*()).
 */
@Entity
@Table(name = "event_member_status", uniqueConstraints = {
        @UniqueConstraint(name = "uq_event_member", columnNames = {"event_id", "group_member_id"})
})
public class EventMemberStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "memberStatuses", "group"})
    private GroupEvent event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_member_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "group", "eventStatuses"})
    private GroupMember groupMember;

    // Optionnels : null pour un événement "sans argent" (voir GroupEvent.hasMoney()).
    @Column(name = "required_amount", precision = 14, scale = 2)
    private BigDecimal requiredAmount;

    @Column(name = "paid_amount", precision = 14, scale = 2)
    private BigDecimal paidAmount;

    // Statut PENDING par défaut pour un événement avec argent ; surchargé en
    // NOT_SEEN à la création par GroupService pour un événement sans argent.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;

    // --- Suivi "vu/pas vu" (événements sans argent uniquement) -------------

    // Jeton public permettant à un membre externe de marquer le rappel comme
    // vu via un lien reçu par email, sans authentification (voir
    // PublicAcknowledgmentController). Généré uniquement pour les
    // événements sans argent.
    @Column(name = "public_ack_token", unique = true)
    private java.util.UUID publicAckToken;

    @Column(name = "seen_at")
    private java.time.OffsetDateTime seenAt;

    // Anti-doublon des relances : le job de notification tourne toutes les
    // minutes pour respecter l'heure réglée sur l'événement (event_time),
    // donc on doit savoir si l'étape courante (ex. "J-7", "J+3 (en retard)")
    // a déjà été envoyée aujourd'hui pour ce membre avant d'en renvoyer une.
    // Voir NotificationSchedulerService.
    @Column(name = "last_reminder_stage", length = 20)
    private String lastReminderStage;

    @Column(name = "last_reminder_sent_at")
    private java.time.OffsetDateTime lastReminderSentAt;

    // Historique complet des versements liés à ce statut. Suppression
    // en cascade réelle : effacer un statut efface son historique.
    @JsonIgnore
    @OneToMany(mappedBy = "eventMemberStatus", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PaymentHistory> paymentHistory = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "eventMemberStatus", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PaymentToken> paymentTokens = new ArrayList<>();

    public EventMemberStatus() {
    }

    public EventMemberStatus(Long id, GroupEvent event, GroupMember groupMember, BigDecimal requiredAmount,
                              BigDecimal paidAmount, PaymentStatus status) {
        this.id = id;
        this.event = event;
        this.groupMember = groupMember;
        this.requiredAmount = requiredAmount;
        this.paidAmount = paidAmount;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public GroupEvent getEvent() {
        return event;
    }

    public void setEvent(GroupEvent event) {
        this.event = event;
    }

    public GroupMember getGroupMember() {
        return groupMember;
    }

    public void setGroupMember(GroupMember groupMember) {
        this.groupMember = groupMember;
    }

    public BigDecimal getRequiredAmount() {
        return requiredAmount;
    }

    public void setRequiredAmount(BigDecimal requiredAmount) {
        this.requiredAmount = requiredAmount;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public void setPaidAmount(BigDecimal paidAmount) {
        this.paidAmount = paidAmount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public String getLastReminderStage() {
        return lastReminderStage;
    }

    public void setLastReminderStage(String lastReminderStage) {
        this.lastReminderStage = lastReminderStage;
    }

    public java.time.OffsetDateTime getLastReminderSentAt() {
        return lastReminderSentAt;
    }

    public void setLastReminderSentAt(java.time.OffsetDateTime lastReminderSentAt) {
        this.lastReminderSentAt = lastReminderSentAt;
    }

    public java.util.UUID getPublicAckToken() {
        return publicAckToken;
    }

    public void setPublicAckToken(java.util.UUID publicAckToken) {
        this.publicAckToken = publicAckToken;
    }

    public java.time.OffsetDateTime getSeenAt() {
        return seenAt;
    }

    public void setSeenAt(java.time.OffsetDateTime seenAt) {
        this.seenAt = seenAt;
    }

    public List<PaymentHistory> getPaymentHistory() {
        return paymentHistory;
    }

    public void setPaymentHistory(List<PaymentHistory> paymentHistory) {
        this.paymentHistory = paymentHistory;
    }

    public List<PaymentToken> getPaymentTokens() {
        return paymentTokens;
    }

    public void setPaymentTokens(List<PaymentToken> paymentTokens) {
        this.paymentTokens = paymentTokens;
    }
}
