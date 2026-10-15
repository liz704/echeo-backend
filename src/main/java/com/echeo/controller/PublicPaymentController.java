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
    private final com.echeo.service.OnlinePaymentService onlinePaymentService;

    public PublicPaymentController(PaymentService paymentService,
                                   com.echeo.service.OnlinePaymentService onlinePaymentService) {
        this.paymentService = paymentService;
        this.onlinePaymentService = onlinePaymentService;
    }

    /** Indique si le paiement en ligne est disponible (clés Faroty configurées). */
    @GetMapping("/online-available")
    public ResponseEntity<java.util.Map<String, Boolean>> onlineAvailable() {
        return ResponseEntity.ok(java.util.Map.of("available", onlinePaymentService.isAvailable()));
    }

    /** Démarre un paiement en ligne : renvoie l'URL de la page de paiement Faroty. */
    @PostMapping("/{token}/online")
    public ResponseEntity<java.util.Map<String, String>> startOnline(
            @PathVariable String token, jakarta.servlet.http.HttpServletRequest request) {
        String fwd = request.getHeader("X-Forwarded-For");
        String ip = fwd != null && !fwd.isBlank() ? fwd.split(",")[0].trim() : request.getRemoteAddr();
        String url = onlinePaymentService.startSession(parseToken(token), ip);
        return ResponseEntity.ok(java.util.Map.of("sessionUrl", url));
    }

    /** Au retour du membre : vérifie auprès de Faroty et enregistre le versement s'il est confirmé. */
    @PostMapping("/{token}/online/refresh")
    public ResponseEntity<Void> refreshOnline(@PathVariable String token) {
        onlinePaymentService.refreshForToken(parseToken(token));
        return ResponseEntity.noContent().build();
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
        java.math.BigDecimal fee = status.getEvent().getWithdrawalFeeAmount();
        // Reste à payer FRAIS DE RETRAIT INCLUS (ce que le membre doit réellement envoyer).
        BigDecimal remaining = com.echeo.util.PaymentRules.remainingToPay(paid, required, fee);
        PublicPaymentDetailsResponse response = new PublicPaymentDetailsResponse(
                status.getEvent().getTitle(),
                status.getGroupMember().getContactFullName(),
                required,
                paid,
                remaining,
                fee,
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
