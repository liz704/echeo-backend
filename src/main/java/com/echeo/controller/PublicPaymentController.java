package com.echeo.controller;

import com.echeo.dto.PublicPaymentDetailsResponse;
import com.echeo.dto.PublicPaymentRequest;
import com.echeo.exception.InvalidArgumentException;
import com.echeo.model.entity.EventMemberStatus;
import com.echeo.model.entity.PaymentToken;
import com.echeo.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Endpoints PUBLICS (aucun JWT requis — voir SecurityConfig : /api/v1/public/**
 * est en permitAll). Le token dans l'URL fait office d'autorisation à lui seul :
 * c'est pourquoi PaymentService valide systématiquement son expiration et son
 * usage unique avant toute lecture/écriture.
 */
@RestController
@RequestMapping("/api/v1/public/pay")
public class PublicPaymentController {

    private final PaymentService paymentService;

    public PublicPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Consultation des détails de l'échéance avant paiement (ne consomme pas le token).
     */
    @GetMapping("/{token}")
    public ResponseEntity<PublicPaymentDetailsResponse> getDetails(@PathVariable String token) {
        UUID tokenUuid = parseToken(token);
        PaymentToken paymentToken = paymentService.peekToken(tokenUuid);
        EventMemberStatus status = paymentToken.getEventMemberStatus();

        BigDecimal required = status.getRequiredAmount();
        BigDecimal paid = status.getPaidAmount();
        BigDecimal remaining = required.subtract(paid);
        if (remaining.compareTo(BigDecimal.ZERO) < 0) {
            remaining = BigDecimal.ZERO;
        }

        PublicPaymentDetailsResponse response = new PublicPaymentDetailsResponse(
                status.getEvent().getTitle(),
                status.getGroupMember().getContactFullName(),
                required,
                paid,
                remaining,
                status.getStatus(),
                paymentToken.getExpiresAt()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Règlement effectif via le lien public : consomme le token, enregistre
     * le versement, et retourne le statut mis à jour.
     */
    @PostMapping("/{token}")
    public ResponseEntity<EventMemberStatus> pay(@PathVariable String token,
                                                   @Valid @RequestBody PublicPaymentRequest request) {
        UUID tokenUuid = parseToken(token);
        EventMemberStatus updated = paymentService.payViaToken(
                tokenUuid, request.getAmount(), request.getPaymentMethod(), request.getTransactionRef()
        );
        return ResponseEntity.ok(updated);
    }

    private UUID parseToken(String token) {
        try {
            return UUID.fromString(token);
        } catch (IllegalArgumentException ex) {
            throw new InvalidArgumentException("Format de lien de paiement invalide.");
        }
    }
}
