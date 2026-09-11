package com.echeo.controller;

import com.echeo.exception.EntityNotFoundException;
import com.echeo.model.entity.NotificationLog;
import com.echeo.repository.NotificationLogRepository;
import com.echeo.security.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * Historique des notifications/relances (email, SMS, WhatsApp) envoyées à
 * l'utilisateur courant. notification_logs n'a pas de clé étrangère vers
 * users (schéma validé en Étape 1) : le rapprochement se fait sur les
 * contacts connus de l'utilisateur (email, téléphone).
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationLogRepository notificationLogRepository;

    public NotificationController(NotificationLogRepository notificationLogRepository) {
        this.notificationLogRepository = notificationLogRepository;
    }

    @GetMapping("/history")
    public ResponseEntity<List<NotificationLog>> history(@AuthenticationPrincipal CustomUserDetails currentUser) {
        List<String> myContacts = new ArrayList<>();
        if (currentUser.getUser().getEmail() != null) {
            myContacts.add(currentUser.getUser().getEmail());
        }
        if (currentUser.getUser().getPhone() != null) {
            myContacts.add(currentUser.getUser().getPhone());
        }

        return ResponseEntity.ok(notificationLogRepository.findByRecipientContactInOrderBySentAtDesc(myContacts));
    }

    /**
     * Supprime une notification de l'historique (le contenu du message,
     * pas l'email lui-même — supprimer ici n'annule pas un envoi déjà fait).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!notificationLogRepository.existsById(id)) {
            throw new EntityNotFoundException("NotificationLog", id);
        }
        notificationLogRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
