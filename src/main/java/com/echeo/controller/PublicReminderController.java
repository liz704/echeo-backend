package com.echeo.controller;

import com.echeo.exception.InvalidArgumentException;
import com.echeo.model.entity.PersonalReminder;
import com.echeo.service.ReminderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Endpoint PUBLIC (aucun JWT requis — voir SecurityConfig : /api/v1/public/**
 * est en permitAll) pour le lien "marquer fait" dans l'email de rappel
 * personnel. Un simple GET suffit (action sans enjeu, idempotente).
 */
@RestController
@RequestMapping("/api/v1/public/reminders")
public class PublicReminderController {

    private final ReminderService reminderService;

    public PublicReminderController(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @GetMapping("/complete/{token}")
    public ResponseEntity<Map<String, Object>> complete(@PathVariable String token) {
        UUID tokenUuid;
        try {
            tokenUuid = UUID.fromString(token);
        } catch (IllegalArgumentException ex) {
            throw new InvalidArgumentException("Lien invalide.");
        }

        PersonalReminder reminder = reminderService.completeByPublicToken(tokenUuid);

        return ResponseEntity.ok(Map.of(
                "title", reminder.getTitle(),
                "completed", reminder.isCompleted()
        ));
    }
}
