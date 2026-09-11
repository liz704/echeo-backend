package com.echeo.repository;

import com.echeo.model.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    // Pas de FK vers users dans notification_logs (schéma validé en Étape 1) :
    // on retrouve l'historique d'un utilisateur en filtrant sur ses contacts
    // connus (email, téléphone) plutôt que par identifiant.
    List<NotificationLog> findByRecipientContactInOrderBySentAtDesc(List<String> recipientContacts);

    Optional<NotificationLog> findByProviderMessageId(String providerMessageId);
}
