package com.echeo.controller;

import com.echeo.model.entity.NotificationLog;
import com.echeo.model.enums.NotificationStatus;
import com.echeo.repository.NotificationLogRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/public/webhooks/brevo")
public class BrevoWebhookController {

    private final NotificationLogRepository notificationLogRepository;

    public BrevoWebhookController(NotificationLogRepository notificationLogRepository) {
        this.notificationLogRepository = notificationLogRepository;
    }

    @PostMapping
    public ResponseEntity<Void> handleBrevoEvent(@RequestBody Map<String, Object> payload) {
        String messageId = payload.containsKey("message-id") ? (String) payload.get("message-id") : null;
        String event = payload.containsKey("event") ? (String) payload.get("event") : null;

        if (messageId == null || event == null) {
            return ResponseEntity.badRequest().build();
        }

        Optional<NotificationLog> logOptional = notificationLogRepository.findByProviderMessageId(messageId);
        if (logOptional.isEmpty()) {
            return ResponseEntity.ok().build();
        }

        NotificationLog notificationLog = logOptional.get();
        switch (event) {
            case "delivered" -> notificationLog.setStatus(NotificationStatus.DELIVERED);
            case "hard_bounce", "soft_bounce", "blocked", "invalid_email" -> notificationLog.setStatus(NotificationStatus.BOUNCED);
            default -> {
                return ResponseEntity.ok().build();
            }
        }
        notificationLogRepository.save(notificationLog);

        return ResponseEntity.ok().build();
    }
}