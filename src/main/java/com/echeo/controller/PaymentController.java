package com.echeo.controller;

import com.echeo.dto.RecordPaymentRequest;
import com.echeo.exception.EntityNotFoundException;
import com.echeo.model.entity.EventMemberStatus;
import com.echeo.model.entity.PaymentHistory;
import com.echeo.model.entity.PaymentToken;
import com.echeo.repository.EventMemberStatusRepository;
import com.echeo.repository.PaymentHistoryRepository;
import com.echeo.security.CustomUserDetails;
import com.echeo.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/**
 * Enregistrement des paiements (Cash / Mobile Money / Card) côté authentifié
 * (ex. un responsable de groupe qui saisit un paiement reçu en espèces),
 * et génération de liens de paiement publics à usage unique.
 * Route publique de paiement via token : voir PublicPaymentController.
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private static final Duration DEFAULT_TOKEN_VALIDITY = Duration.ofDays(7);

    private final PaymentService paymentService;
    private final PaymentHistoryRepository paymentHistoryRepository;
    private final EventMemberStatusRepository eventMemberStatusRepository;

    public PaymentController(PaymentService paymentService,
                              PaymentHistoryRepository paymentHistoryRepository,
                              EventMemberStatusRepository eventMemberStatusRepository) {
        this.paymentService = paymentService;
        this.paymentHistoryRepository = paymentHistoryRepository;
        this.eventMemberStatusRepository = eventMemberStatusRepository;
    }

    @PostMapping
    public ResponseEntity<EventMemberStatus> recordPayment(@Valid @RequestBody RecordPaymentRequest request) {
        EventMemberStatus updated = paymentService.recordPayment(
                request.getEventMemberStatusId(), request.getAmount(),
                request.getPaymentMethod(), request.getTransactionRef()
        );
        return ResponseEntity.ok(updated);
    }

    /**
     * "Mes cotisations" : toutes les échéances de paiement (tous groupes
     * confondus) où l'utilisateur courant est le membre concerné.
     * Utilisé par la page frontend d'historique des paiements.
     */
    @GetMapping("/me")
    public ResponseEntity<List<EventMemberStatus>> myStatuses(@AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(eventMemberStatusRepository.findByGroupMember_User_IdOrderByIdDesc(currentUser.getUserId()));
    }

    @GetMapping("/{eventMemberStatusId}/history")
    public ResponseEntity<List<PaymentHistory>> history(@PathVariable Long eventMemberStatusId) {
        return ResponseEntity.ok(
                paymentHistoryRepository.findByEventMemberStatus_IdOrderByPaidAtDesc(eventMemberStatusId)
        );
    }

    /**
     * Supprime une entrée d'historique de paiement (archivage manuel).
     * Ne modifie ni ne recalcule le statut/montant de l'échéance associée.
     */
    @DeleteMapping("/history/{paymentHistoryId}")
    public ResponseEntity<Void> deleteHistoryEntry(@PathVariable Long paymentHistoryId) {
        if (!paymentHistoryRepository.existsById(paymentHistoryId)) {
            throw new EntityNotFoundException("PaymentHistory", paymentHistoryId);
        }
        paymentHistoryRepository.deleteById(paymentHistoryId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Génère un lien de paiement public (valide 7 jours par défaut) pour un
     * EventMemberStatus donné — à envoyer par email/SMS au membre concerné.
     * Le lien final côté frontend sera de la forme /pay/{tokenUuid}, consommé
     * par PublicPaymentController.
     */
    @PostMapping("/{eventMemberStatusId}/token")
    public ResponseEntity<PaymentToken> generateToken(@PathVariable Long eventMemberStatusId) {
        PaymentToken token = paymentService.generatePaymentToken(eventMemberStatusId, DEFAULT_TOKEN_VALIDITY);
        return ResponseEntity.status(HttpStatus.CREATED).body(token);
    }
}
