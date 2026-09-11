package com.echeo.service;

import com.echeo.dto.PaymentSimulationRequest;
import com.echeo.exception.InvalidArgumentException;
import com.echeo.model.entity.EventMemberStatus;
import com.echeo.model.entity.PaymentToken;
import com.echeo.model.enums.PaymentMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Simule un webhook de paiement (MOMO/OM/CARD) pour tester le comportement
 * réel du système SANS dépendre d'un vrai fournisseur Mobile Money — utile
 * pour valider l'idempotence et les transactions avant qu'un vrai fournisseur
 * ne soit branché.
 *
 * Réutilise volontairement le chemin de code réel (PaymentService.recordPayment,
 * le même que PublicPaymentController) plutôt que de dupliquer la logique
 * métier : c'est justement ce qui garantit que le test reflète le
 * comportement réel, pas un chemin parallèle qui pourrait diverger.
 *
 * Bloqué en profil "prod" : voir isSimulationAllowed().
 */
@Service
public class PaymentSimulationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentSimulationService.class);

    private final Environment environment;
    private final PaymentService paymentService;

    public PaymentSimulationService(Environment environment, PaymentService paymentService) {
        this.environment = environment;
        this.paymentService = paymentService;
    }

    @Transactional
    public EventMemberStatus simulate(PaymentSimulationRequest request) {
        if (!isSimulationAllowed()) {
            throw new InvalidArgumentException(
                    "La simulation de paiement n'est disponible qu'en profil 'dev' ou 'test' (profil actif : "
                            + String.join(",", environment.getActiveProfiles()) + ").");
        }

        String mockTransactionRef = "MOCK-TXN-" + UUID.randomUUID();
        PaymentMethod mappedMethod = mapPaymentMethod(request.getPaymentMethod());

        if (request.getSimulatedStatus() == PaymentSimulationRequest.SimulatedStatus.SUCCESS) {
            // Passe par le MÊME chemin qu'un vrai paiement via lien public :
            // consomme le token, applique recordPayment (calcul PAID/PARTIAL/
            // SURPLUS, historique, reçu, copie propriétaire — tout est réel).
            EventMemberStatus updated = paymentService.payViaToken(
                    request.getPaymentToken(), request.getAmountPaid(), mappedMethod, mockTransactionRef);

            log.info("[SIMULATION] Paiement simulé SUCCESS — token={}, référence={}, statut résultant={}",
                    request.getPaymentToken(), mockTransactionRef, updated.getStatus());
            return updated;
        }

        // FAILED : on ne touche à rien (l'échéance reste dans son état actuel,
        // PENDING ou PARTIALLY_PAID selon l'historique réel) — on trace
        // seulement la tentative dans les logs applicatifs.
        PaymentToken peeked;
        try {
            peeked = paymentService.peekToken(request.getPaymentToken());
        } catch (Exception ex) {
            log.warn("[SIMULATION] Paiement simulé FAILED — token invalide/expiré ({}) : {}",
                    request.getPaymentToken(), ex.getMessage());
            throw ex;
        }

        log.warn("[SIMULATION] Paiement simulé FAILED — token={}, montant tenté={}, échéance laissée à {}",
                request.getPaymentToken(), request.getAmountPaid(), peeked.getEventMemberStatus().getStatus());
        return peeked.getEventMemberStatus();
    }

    private PaymentMethod mapPaymentMethod(PaymentSimulationRequest.SimulatedPaymentMethod simulated) {
        return switch (simulated) {
            case MOMO, OM -> PaymentMethod.MOBILE_MONEY;
            case CARD -> PaymentMethod.CARD;
        };
    }

    private boolean isSimulationAllowed() {
        for (String profile : environment.getActiveProfiles()) {
            if (profile.equalsIgnoreCase("dev") || profile.equalsIgnoreCase("test")) {
                return true;
            }
        }
        // Aucun profil actif (cas par défaut en développement local sans
        // -Dspring.profiles.active) est traité comme "dev" pour ne pas
        // bloquer les tests locaux par défaut.
        return environment.getActiveProfiles().length == 0;
    }
}
