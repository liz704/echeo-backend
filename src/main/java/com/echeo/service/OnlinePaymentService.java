package com.echeo.service;

import com.echeo.exception.InvalidArgumentException;
import com.echeo.model.entity.EventMemberStatus;
import com.echeo.model.entity.PaymentSession;
import com.echeo.model.entity.PaymentToken;
import com.echeo.model.enums.PaymentMethod;
import com.echeo.repository.PaymentSessionRepository;
import com.echeo.util.PaymentRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Paiement en ligne via Faroty Pay depuis le lien public.
 * Le montant demandé = reste à payer, frais de retrait inclus (PaymentRules).
 * Le versement n'est enregistré QU'APRÈS confirmation de Faroty (jamais sur la parole du navigateur),
 * une seule fois par session (champ processed), soit au retour du membre soit par le job de rattrapage.
 */
@Service
public class OnlinePaymentService {

    private static final Logger log = LoggerFactory.getLogger(OnlinePaymentService.class);

    private final FarotyPayClient faroty;
    private final PaymentService paymentService;
    private final PaymentSessionRepository sessionRepository;

    @Value("${echeo.frontend.base-url:https://echeo-one.vercel.app}")
    private String frontendBaseUrl;

    public OnlinePaymentService(FarotyPayClient faroty, PaymentService paymentService,
                                PaymentSessionRepository sessionRepository) {
        this.faroty = faroty;
        this.paymentService = paymentService;
        this.sessionRepository = sessionRepository;
    }

    public boolean isAvailable() {
        return faroty.isConfigured();
    }

    @Transactional
    public String startSession(UUID tokenUuid, String clientIp) {
        PaymentToken token = paymentService.peekToken(tokenUuid);
        EventMemberStatus status = token.getEventMemberStatus();
        BigDecimal remaining = PaymentRules.remainingToPay(
                status.getPaidAmount(), status.getRequiredAmount(), status.getEvent().getWithdrawalFeeAmount());
        if (remaining.signum() <= 0) {
            throw new InvalidArgumentException("Rien à payer : cette échéance est déjà réglée.");
        }
        String base = (frontendBaseUrl == null || frontendBaseUrl.isBlank()
                ? "https://echeo-one.vercel.app" : frontendBaseUrl).replaceAll("/+$", "");
        String page = base + "/pay/" + tokenUuid;
        FarotyPayClient.Session session = faroty.createSession(
                remaining, page + "?online=success", page + "?online=cancel", clientIp);

        PaymentSession ps = new PaymentSession();
        ps.setSessionToken(session.sessionToken());
        ps.setPaymentTokenUuid(tokenUuid);
        ps.setEventMemberStatus(status);
        ps.setAmount(remaining);
        sessionRepository.save(ps);
        return session.sessionUrl();
    }

    /** Vérifie les sessions en attente de ce lien (appelé quand le membre revient sur la page). */
    @Transactional
    public void refreshForToken(UUID tokenUuid) {
        for (PaymentSession s : sessionRepository.findByPaymentTokenUuidAndStatusAndProcessedFalse(tokenUuid, "PENDING")) {
            check(s);
        }
    }

    /** Rattrapage : toutes les minutes, sessions des dernières 48 h encore en attente. */
    @Scheduled(cron = "30 * * * * *")
    @Transactional
    public void reconcilePending() {
        if (!faroty.isConfigured()) {
            return;
        }
        List<PaymentSession> pending = sessionRepository.findByStatusAndProcessedFalseAndCreatedAtAfter(
                "PENDING", OffsetDateTime.now().minusHours(48));
        for (PaymentSession s : pending) {
            check(s);
        }
    }

    private void check(PaymentSession s) {
        try {
            FarotyPayClient.TxStatus tx = faroty.getStatus(s.getSessionToken());
            s.setLastCheckedAt(OffsetDateTime.now());
            if (tx.successful() && !s.isProcessed()) {
                s.setStatus("SUCCESS");
                s.setProcessed(true);
                sessionRepository.save(s);
                paymentService.recordPayment(s.getEventMemberStatus().getId(), s.getAmount(),
                        PaymentMethod.MOBILE_MONEY, "FAROTY:" + s.getSessionToken());
                log.info("Paiement en ligne enregistré (session {}, {} FCFA)", s.getSessionToken(), s.getAmount());
            } else if (tx.failed()) {
                s.setStatus("FAILED");
                sessionRepository.save(s);
            } else {
                sessionRepository.save(s);
            }
        } catch (Exception ex) {
            log.warn("Vérification de la session Faroty {} impossible : {}", s.getSessionToken(), ex.getMessage());
        }
    }
}
