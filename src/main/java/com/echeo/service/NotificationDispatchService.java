package com.echeo.service;

import com.echeo.model.entity.Group;
import com.echeo.model.entity.GroupMember;
import com.echeo.model.entity.NotificationLog;
import com.echeo.model.enums.NotificationStatus;
import com.echeo.model.enums.NotificationType;
import com.echeo.repository.NotificationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Point unique d'envoi des e-mails (relances, reçus, copies propriétaire).
 *
 * - Chaque envoi est journalisé avec son objet et son texte.
 * - Si l'envoi échoue (clé Brevo manquante, quota atteint, panne réseau...), le message reste
 *   en FAILED et il est renvoyé AUTOMATIQUEMENT par retryFailed() dès que la cause est réglée,
 *   sans rien redéclencher côté métier (aucun doublon de relance n'est créé).
 * - Les relances anciennes (sans objet enregistré) ne sont jamais rejouées.
 */
@Service
public class NotificationDispatchService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchService.class);
    private static final int MAX_RETRIES = 80;
    private static final Duration MAX_AGE = Duration.ofDays(3);

    private final NotificationLogRepository notificationLogRepository;
    private final EmailService emailService;

    public NotificationDispatchService(NotificationLogRepository notificationLogRepository,
                                       EmailService emailService) {
        this.notificationLogRepository = notificationLogRepository;
        this.emailService = emailService;
    }

    /** Journalise puis tente l'envoi. Ne lève jamais d'exception. */
    public NotificationLog sendEmail(String recipient, String subject, String body) {
        NotificationLog entry = new NotificationLog();
        entry.setRecipientContact(recipient);
        entry.setType(NotificationType.EMAIL);
        entry.setSubject(subject);
        entry.setMessageContent(body);
        entry.setStatus(NotificationStatus.PENDING);
        entry = notificationLogRepository.save(entry);
        attempt(entry);
        return notificationLogRepository.save(entry);
    }

    private boolean attempt(NotificationLog entry) {
        entry.setLastAttemptAt(OffsetDateTime.now());
        if (entry.getSentAt() == null) {
            entry.setSentAt(OffsetDateTime.now());
        }
        try {
            String messageId = emailService.send(entry.getRecipientContact(), entry.getSubject(), entry.getMessageContent());
            entry.setProviderMessageId(messageId);
            entry.setStatus(NotificationStatus.SENT);
            return true;
        } catch (Exception ex) {
            log.warn("Envoi e-mail à {} échoué (sera retenté automatiquement) : {}",
                    entry.getRecipientContact(), ex.getMessage());
            entry.setStatus(NotificationStatus.FAILED);
            return false;
        }
    }

    /** Toutes les 2 minutes : renvoie les e-mails FAILED (espacés de plus en plus). */
    @Scheduled(fixedDelay = 120_000, initialDelay = 90_000)
    @Transactional
    public void retryFailed() {
        List<NotificationLog> failed = notificationLogRepository
                .findTop50ByStatusAndSubjectIsNotNullAndRetryCountLessThanOrderByIdAsc(
                        NotificationStatus.FAILED, MAX_RETRIES);
        OffsetDateTime now = OffsetDateTime.now();
        for (NotificationLog entry : failed) {
            if (entry.getType() != NotificationType.EMAIL) {
                continue;
            }
            OffsetDateTime first = entry.getSentAt();
            if (first != null && first.isBefore(now.minus(MAX_AGE))) {
                entry.setRetryCount(MAX_RETRIES); // abandon : trop ancien
                notificationLogRepository.save(entry);
                continue;
            }
            Duration wait = entry.getRetryCount() < 12 ? Duration.ofMinutes(5) : Duration.ofMinutes(60);
            OffsetDateTime last = entry.getLastAttemptAt();
            if (last != null && last.isAfter(now.minus(wait))) {
                continue;
            }
            entry.setRetryCount(entry.getRetryCount() + 1);
            if (attempt(entry)) {
                log.info("E-mail {} renvoyé avec succès après {} tentative(s).", entry.getId(), entry.getRetryCount());
            }
            notificationLogRepository.save(entry);
        }
    }

    /**
     * Langue d'un message destiné à un membre : langue choisie pour ce membre,
     * sinon langue du propriétaire du groupe, sinon français.
     */
    public static String resolveLocale(GroupMember member, Group group) {
        String l = member != null ? member.getPreferredLocale() : null;
        if (l == null || l.isBlank()) {
            l = (group != null && group.getOwner() != null) ? group.getOwner().getPreferredLocale() : null;
        }
        return normalizeLocale(l);
    }

    public static String ownerLocale(Group group) {
        return normalizeLocale(group != null && group.getOwner() != null ? group.getOwner().getPreferredLocale() : null);
    }

    public static String normalizeLocale(String l) {
        return l != null && l.toLowerCase().startsWith("en") ? "en" : "fr";
    }
}
