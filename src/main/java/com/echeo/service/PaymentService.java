package com.echeo.service;

import com.echeo.exception.EntityNotFoundException;
import com.echeo.exception.InvalidArgumentException;
import com.echeo.model.entity.EventMemberStatus;
import com.echeo.model.entity.GroupMember;
import com.echeo.model.entity.NotificationLog;
import com.echeo.model.entity.PaymentHistory;
import com.echeo.model.entity.PaymentToken;
import com.echeo.model.enums.NotificationStatus;
import com.echeo.model.enums.NotificationType;
import com.echeo.model.enums.PaymentMethod;
import com.echeo.model.enums.PaymentStatus;
import com.echeo.repository.EventMemberStatusRepository;
import com.echeo.repository.NotificationLogRepository;
import com.echeo.repository.PaymentHistoryRepository;
import com.echeo.repository.PaymentTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Logique métier des paiements : enregistrement des versements avec calcul
 * strict du statut (PARTIALLY_PAID / PAID / SURPLUS), historisation,
 * génération/validation de jetons de paiement à usage unique, et envoi
 * d'un reçu au membre + copie récapitulative au propriétaire du groupe.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final DateTimeFormatter RECEIPT_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm", Locale.FRENCH);

    private final EventMemberStatusRepository eventMemberStatusRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;
    private final PaymentTokenRepository paymentTokenRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final EmailService emailService;

    public PaymentService(EventMemberStatusRepository eventMemberStatusRepository,
                           PaymentHistoryRepository paymentHistoryRepository,
                           PaymentTokenRepository paymentTokenRepository,
                           NotificationLogRepository notificationLogRepository,
                           EmailService emailService) {
        this.eventMemberStatusRepository = eventMemberStatusRepository;
        this.paymentHistoryRepository = paymentHistoryRepository;
        this.paymentTokenRepository = paymentTokenRepository;
        this.notificationLogRepository = notificationLogRepository;
        this.emailService = emailService;
    }

    /**
     * Enregistre un versement pour un EventMemberStatus donné : met à jour
     * le montant payé, recalcule le statut (PARTIALLY_PAID / PAID / SURPLUS),
     * et journalise le versement dans payment_history. Opération atomique.
     *
     * @param eventMemberStatusId identifiant du statut de paiement ciblé
     * @param amount              montant versé (doit être strictement positif)
     * @param paymentMethod       moyen de paiement utilisé
     * @param transactionRef      référence externe de transaction (nullable)
     * @return le EventMemberStatus mis à jour, avec son statut recalculé
     */
    @Transactional
    public EventMemberStatus recordPayment(Long eventMemberStatusId, BigDecimal amount,
                                            PaymentMethod paymentMethod, String transactionRef) {
        if (eventMemberStatusId == null) {
            throw new InvalidArgumentException("L'identifiant du statut de paiement est obligatoire.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidArgumentException("Le montant versé doit être strictement positif.");
        }
        if (paymentMethod == null) {
            throw new InvalidArgumentException("Le moyen de paiement est obligatoire.");
        }

        EventMemberStatus status = eventMemberStatusRepository.findById(eventMemberStatusId)
                .orElseThrow(() -> new EntityNotFoundException("EventMemberStatus", eventMemberStatusId));

        BigDecimal previousPaid = status.getPaidAmount() != null ? status.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal requiredAmount = status.getRequiredAmount();
        if (requiredAmount == null) {
            throw new InvalidArgumentException("Le montant requis (required_amount) n'est pas défini pour ce statut.");
        }

        BigDecimal newPaidAmount = previousPaid.add(amount);
        int comparison = newPaidAmount.compareTo(requiredAmount);

        BigDecimal surplusAmount = BigDecimal.ZERO;
        PaymentStatus newStatus;
        if (comparison < 0) {
            newStatus = PaymentStatus.PARTIALLY_PAID;
        } else if (comparison == 0) {
            newStatus = PaymentStatus.PAID;
        } else {
            newStatus = PaymentStatus.SURPLUS;
            surplusAmount = newPaidAmount.subtract(requiredAmount);
        }

        status.setPaidAmount(newPaidAmount);
        status.setStatus(newStatus);
        eventMemberStatusRepository.save(status);

        PaymentHistory history = new PaymentHistory();
        history.setEventMemberStatus(status);
        history.setAmountPaid(amount);
        history.setPaymentMethod(paymentMethod);
        history.setTransactionRef(transactionRef);
        history.setPaidAt(OffsetDateTime.now());
        paymentHistoryRepository.save(history);

        // Le surplus est déjà reflété dans paid_amount (paid_amount > required_amount) ;
        // surplusAmount reste disponible ici pour un futur usage (ex. notification,
        // crédit sur un prochain événement) sans nécessiter de nouveau calcul.
        if (newStatus == PaymentStatus.SURPLUS) {
            logSurplus(status, surplusAmount);
        }

        sendReceipt(status, amount, paymentMethod, transactionRef, history.getPaidAt());

        return status;
    }

    /**
     * Envoie un reçu de paiement mis en forme au membre concerné, et une
     * copie récapitulative au propriétaire du groupe (sauf préférence
     * contraire — voir Group.notifyOwnerOnReminders, réutilisée ici aussi
     * puisque c'est la même notion de "copie propriétaire").
     */
    private void sendReceipt(EventMemberStatus status, BigDecimal amountPaid, PaymentMethod paymentMethod,
                              String transactionRef, OffsetDateTime paidAt) {
        GroupMember member = status.getGroupMember();
        String receiptText = buildReceiptText(status, amountPaid, paymentMethod, transactionRef, paidAt);

        String memberContact = member.getContactEmail() != null ? member.getContactEmail() : member.getContactPhone();
        logAndSendEmail(memberContact, "ÉCHÉO — Reçu de paiement", receiptText);

        var group = status.getEvent().getGroup();
        if (group.isNotifyOwnerOnReminders()) {
            String ownerCopy = "Copie pour information (propriétaire du groupe) :\n\n" + receiptText;
            logAndSendEmail(group.getOwner().getEmail(), "ÉCHÉO — Paiement reçu dans votre groupe", ownerCopy);
        }
    }

    private String buildReceiptText(EventMemberStatus status, BigDecimal amountPaid, PaymentMethod paymentMethod,
                                     String transactionRef, OffsetDateTime paidAt) {
        String separator = "----------------------------------------";
        StringBuilder receipt = new StringBuilder();
        receipt.append(separator).append('\n');
        receipt.append("           REÇU DE PAIEMENT — ÉCHÉO\n");
        receipt.append(separator).append('\n');
        receipt.append("Événement       : ").append(status.getEvent().getTitle()).append('\n');
        receipt.append("Membre          : ").append(status.getGroupMember().getContactFullName()).append('\n');
        receipt.append("Date            : ").append(paidAt.format(RECEIPT_DATE_FORMAT)).append('\n');
        receipt.append("Montant versé   : ").append(formatAmount(amountPaid)).append(" FCFA\n");
        receipt.append("Moyen           : ").append(paymentMethod).append('\n');
        if (transactionRef != null && !transactionRef.isBlank()) {
            receipt.append("Référence       : ").append(transactionRef).append('\n');
        }
        receipt.append(separator).append('\n');
        receipt.append("Total requis    : ").append(formatAmount(status.getRequiredAmount())).append(" FCFA\n");
        receipt.append("Total payé      : ").append(formatAmount(status.getPaidAmount())).append(" FCFA\n");
        receipt.append("Statut          : ").append(status.getStatus()).append('\n');
        receipt.append(separator).append('\n');
        receipt.append("Merci — ceci est un reçu généré automatiquement par ÉCHÉO.");
        return receipt.toString();
    }

    private String formatAmount(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }

    private void logAndSendEmail(String recipientContact, String subject, String body) {
        if (recipientContact == null || recipientContact.isBlank()) {
            return;
        }

        NotificationLog notificationLog = new NotificationLog();
        notificationLog.setRecipientContact(recipientContact);
        notificationLog.setType(NotificationType.EMAIL);
        notificationLog.setMessageContent(body);
        notificationLog.setStatus(NotificationStatus.PENDING);
        notificationLogRepository.save(notificationLog);

        boolean sent;
        try {
            String messageId = emailService.send(recipientContact, subject, body);
            notificationLog.setProviderMessageId(messageId);
            sent = true;
        } catch (Exception ex) {
            log.warn("Échec de l'envoi du reçu à {} : {}", recipientContact, ex.getMessage());
            sent = false;
        }

        notificationLog.setSentAt(OffsetDateTime.now());
        notificationLog.setStatus(sent ? NotificationStatus.SENT : NotificationStatus.FAILED);
        notificationLogRepository.save(notificationLog);
    }

    /**
     * Point d'extension : trace/traite le surplus détecté. Actuellement un simple
     * hook (log applicatif côté appelant) — à enrichir en Étape 3 si le besoin
     * métier est de créditer automatiquement un autre événement du même groupe.
     */
    private void logSurplus(EventMemberStatus status, BigDecimal surplusAmount) {
        // Volontairement minimal à ce stade : le montant du surplus est calculable
        // à tout moment via (paid_amount - required_amount) et n'est pas dupliqué en colonne dédiée,
        // conformément au schéma validé en Étape 1.
        if (surplusAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidArgumentException("Incohérence : surplus calculé non positif.");
        }
    }

    /**
     * Génère un jeton de paiement à usage unique pour un lien de paiement public
     * (ex. envoyé par email/SMS pour permettre un règlement sans authentification complète).
     *
     * @param eventMemberStatusId identifiant du statut de paiement à régler
     * @param validity             durée de validité du jeton avant expiration
     */
    @Transactional
    public PaymentToken generatePaymentToken(Long eventMemberStatusId, Duration validity) {
        if (eventMemberStatusId == null) {
            throw new InvalidArgumentException("L'identifiant du statut de paiement est obligatoire.");
        }
        if (validity == null || validity.isZero() || validity.isNegative()) {
            throw new InvalidArgumentException("La durée de validité du jeton doit être strictement positive.");
        }

        EventMemberStatus status = eventMemberStatusRepository.findById(eventMemberStatusId)
                .orElseThrow(() -> new EntityNotFoundException("EventMemberStatus", eventMemberStatusId));

        PaymentToken token = new PaymentToken();
        token.setTokenUuid(UUID.randomUUID());
        token.setEventMemberStatus(status);
        token.setExpiresAt(OffsetDateTime.now().plus(validity));
        token.setUsed(false);

        return paymentTokenRepository.save(token);
    }

    /**
     * Valide un jeton de paiement public (non expiré, non déjà utilisé) et le
     * marque comme consommé. À appeler avant tout traitement d'un paiement
     * initié via lien public.
     */
    @Transactional
    public EventMemberStatus validateAndConsumeToken(UUID tokenUuid) {
        if (tokenUuid == null) {
            throw new InvalidArgumentException("Le jeton de paiement est obligatoire.");
        }

        Optional<PaymentToken> tokenOptional = paymentTokenRepository.findByTokenUuid(tokenUuid);
        PaymentToken token = tokenOptional
                .orElseThrow(() -> new EntityNotFoundException("PaymentToken introuvable pour l'UUID : " + tokenUuid));

        if (token.isUsed()) {
            throw new InvalidArgumentException("Ce lien de paiement a déjà été utilisé.");
        }
        if (token.getExpiresAt() != null && token.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new InvalidArgumentException("Ce lien de paiement a expiré.");
        }

        token.setUsed(true);
        paymentTokenRepository.save(token);

        return token.getEventMemberStatus();
    }

    /**
     * Lecture seule d'un token de paiement public : vérifie sa validité
     * (existence, non expiré, non utilisé) SANS le consommer. Utilisé par
     * l'écran public qui affiche les détails de l'échéance avant paiement.
     */
    @Transactional(readOnly = true)
    public PaymentToken peekToken(UUID tokenUuid) {
        if (tokenUuid == null) {
            throw new InvalidArgumentException("Le jeton de paiement est obligatoire.");
        }

        PaymentToken token = paymentTokenRepository.findByTokenUuid(tokenUuid)
                .orElseThrow(() -> new EntityNotFoundException("PaymentToken introuvable pour l'UUID : " + tokenUuid));

        if (token.isUsed()) {
            throw new InvalidArgumentException("Ce lien de paiement a déjà été utilisé.");
        }
        if (token.getExpiresAt() != null && token.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new InvalidArgumentException("Ce lien de paiement a expiré.");
        }

        return token;
    }

    /**
     * Paiement via lien public : consomme le token puis enregistre le versement
     * sur l'EventMemberStatus associé, de façon atomique.
     */
    @Transactional
    public EventMemberStatus payViaToken(UUID tokenUuid, BigDecimal amount, PaymentMethod paymentMethod,
                                          String transactionRef) {
        EventMemberStatus status = validateAndConsumeToken(tokenUuid);
        return recordPayment(status.getId(), amount, paymentMethod, transactionRef);
    }
}
