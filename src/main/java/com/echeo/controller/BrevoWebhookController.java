package com.echeo.controller;

import com.echeo.model.entity.NotificationLog;
import com.echeo.model.enums.NotificationStatus;
import com.echeo.repository.NotificationLogRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Reçoit les événements de livraison envoyés par Brevo (delivered, hard_bounce,
 * soft_bounce...) pour transformer un statut SENT (envoyé, sans garantie) en
 * DELIVERED ou BOUNCED (confirmation réelle). Route PUBLIQUE (voir
 * SecurityConfig : /api/v1/public/** est en permitAll) — Brevo n'envoie pas
 * de JWT, seul le contenu de l'événement fait foi.
 *
 * À configurer côté Brevo : Dashboard > Transactional > Settings > Webhooks,
 * URL = https://ton-backend.onrender.com/api/v1/public/webhooks/brevo
 * (donc utilisable seulement une fois le backend déployé — voir Étape 6).
 */
@RestController
@RequestMapping("/api/v1/public/webhooks/brevo")
public class BrevoWebhookController {

    private final NotificationLogRepository notificationLogRepository;

    public BrevoWebhookController(NotificationLogRepository notificationLogRepository) {
        this.notificationLogRepository = notificationLogRepository;
    }

    @PostMapping
    public ResponseEntity<Void> handleBrevoEvent(@RequestBody JsonNode payload) {
        String messageId = payload.has("message-id") ? payload.get("message-id").asText() : null;
        String event = payload.has("event") ? payload.get("event").asText() : null;

        if (messageId == null || event == null) {
            return ResponseEntity.badRequest().build();
        }

        Optional<NotificationLog> logOptional = notificationLogRepository.findByProviderMessageId(messageId);
        if (logOptional.isEmpty()) {
            // Rien à faire : soit ce n'est pas un email envoyé par nous,
            // soit le messageId ne correspond à rien de connu — on ne
            // renvoie pas d'erreur pour autant, Brevo n'a pas à retenter.
            return ResponseEntity.ok().build();
        }

        NotificationLog notificationLog = logOptional.get();
        switch (event) {
            case "delivered" -> notificationLog.setStatus(NotificationStatus.DELIVERED);
            case "hard_bounce", "soft_bounce", "blocked", "invalid_email" -> notificationLog.setStatus(NotificationStatus.BOUNCED);
            default -> {
                // Autres événements Brevo (opened, clicked...) : pas pertinents
                // pour notre suivi de livraison, on les ignore silencieusement.
                return ResponseEntity.ok().build();
            }
        }
        notificationLogRepository.save(notificationLog);

        return ResponseEntity.ok().build();
    }
}
