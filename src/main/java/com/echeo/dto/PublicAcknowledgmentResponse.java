package com.echeo.dto;

import com.echeo.model.enums.PaymentStatus;

import java.time.OffsetDateTime;

/**
 * Réponse renvoyée par le lien public "marquer comme vu" (événements de
 * groupe sans argent). Contrairement au paiement, aucune confirmation
 * supplémentaire n'est demandée : visiter le lien suffit à marquer le
 * statut comme SEEN.
 */
public class PublicAcknowledgmentResponse {

    private final String eventTitle;
    private final String memberFullName;
    private final PaymentStatus status;
    private final OffsetDateTime seenAt;

    public PublicAcknowledgmentResponse(String eventTitle, String memberFullName,
                                         PaymentStatus status, OffsetDateTime seenAt) {
        this.eventTitle = eventTitle;
        this.memberFullName = memberFullName;
        this.status = status;
        this.seenAt = seenAt;
    }

    public String getEventTitle() {
        return eventTitle;
    }

    public String getMemberFullName() {
        return memberFullName;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public OffsetDateTime getSeenAt() {
        return seenAt;
    }
}
