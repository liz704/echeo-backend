package com.echeo.controller;

import com.echeo.dto.PaymentSimulationRequest;
import com.echeo.model.entity.EventMemberStatus;
import com.echeo.service.PaymentSimulationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint réservé aux profils dev/test (voir PaymentSimulationService) pour
 * simuler un webhook de paiement Mobile Money/Carte sans fournisseur réel.
 * Authentifié comme le reste de l'API (pas dans /api/v1/public/**) : un
 * développeur testant l'appli doit être connecté, pas n'importe qui.
 */
@RestController
@RequestMapping("/api/v1/dev/payment-simulation")
public class PaymentSimulationController {

    private final PaymentSimulationService paymentSimulationService;

    public PaymentSimulationController(PaymentSimulationService paymentSimulationService) {
        this.paymentSimulationService = paymentSimulationService;
    }

    @PostMapping
    public ResponseEntity<EventMemberStatus> simulate(@Valid @RequestBody PaymentSimulationRequest request) {
        return ResponseEntity.ok(paymentSimulationService.simulate(request));
    }
}
