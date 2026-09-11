package com.echeo.model.entity;

import com.echeo.model.enums.NotificationStatus;
import com.echeo.model.enums.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Journal des notifications/relances envoyées (email, SMS, WhatsApp),
 * indépendant des entités métier pour conserver un historique même
 * si l'événement ou l'utilisateur source est ensuite supprimé.
 */
@Entity
@Table(name = "notification_logs")
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "recipient_contact", nullable = false, length = 255)
    private String recipientContact;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private NotificationType type;

    @Column(name = "message_content", nullable = false, columnDefinition = "TEXT")
    private String messageContent;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NotificationStatus status = NotificationStatus.PENDING;

    // Identifiant renvoyé par le fournisseur d'email (Brevo) à l'envoi,
    // utilisé pour faire le lien avec les événements de livraison reçus
    // par webhook (DELIVERED / BOUNCED).
    @Column(name = "provider_message_id")
    private String providerMessageId;

    public NotificationLog() {
    }

    public NotificationLog(Long id, String recipientContact, NotificationType type, String messageContent,
                            OffsetDateTime sentAt, NotificationStatus status) {
        this.id = id;
        this.recipientContact = recipientContact;
        this.type = type;
        this.messageContent = messageContent;
        this.sentAt = sentAt;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRecipientContact() {
        return recipientContact;
    }

    public void setRecipientContact(String recipientContact) {
        this.recipientContact = recipientContact;
    }

    public NotificationType getType() {
        return type;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }

    public String getMessageContent() {
        return messageContent;
    }

    public void setMessageContent(String messageContent) {
        this.messageContent = messageContent;
    }

    public OffsetDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(OffsetDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public void setStatus(NotificationStatus status) {
        this.status = status;
    }

    public String getProviderMessageId() {
        return providerMessageId;
    }

    public void setProviderMessageId(String providerMessageId) {
        this.providerMessageId = providerMessageId;
    }
}
