package com.echeo.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Envoi d'email réel via l'API Brevo (anciennement Sendinblue — 300 emails/jour
 * gratuits, sans carte bancaire). Contrairement au SMTP simple utilisé
 * auparavant, l'API Brevo renvoie un messageId exploitable pour le suivi de
 * livraison réel (voir BrevoWebhookController) : SENT ne veut dire
 * qu'"accepté par Brevo", DELIVERED/BOUNCED arrive plus tard par webhook.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final String BREVO_SEND_URL = "https://api.brevo.com/v3/smtp/email";

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${echeo.brevo.api-key:}")
    private String brevoApiKey;

    @Value("${echeo.mail.from}")
    private String fromAddress;

    @Value("${echeo.mail.from-name:ÉCHÉO}")
    private String fromName;

    /**
     * Envoie un email et renvoie le messageId Brevo (nécessaire pour relier
     * plus tard l'événement de livraison webhook à ce NotificationLog).
     * En cas d'échec, lève une RuntimeException avec le détail Brevo (status + body)
     * pour que les logs backend affichent clairement la cause.
     */
    public String send(String to, String subject, String body) {
        if (brevoApiKey == null || brevoApiKey.isBlank()) {
            String msg = "Clé API Brevo absente ou vide (echeo.brevo.api-key). Impossible d'envoyer l'email.";
            log.error(msg);
            throw new IllegalStateException(msg);
        }
        if (fromAddress == null || fromAddress.isBlank()) {
            String msg = "Adresse expéditeur absente (echeo.mail.from). Impossible d'envoyer l'email.";
            log.error(msg);
            throw new IllegalStateException(msg);
        }

        log.info("Envoi email Brevo → to={}, from={}, subject={}", to, fromAddress, subject);

        HttpHeaders headers = new HttpHeaders();
        headers.set("api-key", brevoApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        Map<String, Object> sender = new HashMap<>();
        sender.put("email", fromAddress);
        sender.put("name", fromName);

        Map<String, Object> recipient = new HashMap<>();
        recipient.put("email", to);

        Map<String, Object> payload = new HashMap<>();
        payload.put("sender", sender);
        payload.put("to", List.of(recipient));
        payload.put("subject", subject);
        payload.put("textContent", body);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    BREVO_SEND_URL, HttpMethod.POST, request, JsonNode.class);

            HttpStatusCode status = response.getStatusCode();
            JsonNode responseBody = response.getBody();

            if (!status.is2xxSuccessful()) {
                String detail = responseBody != null ? responseBody.toString() : "(corps vide)";
                String msg = String.format(
                        "Brevo a refusé l'envoi (HTTP %s) vers %s : %s",
                        status.value(), to, detail);
                log.error(msg);
                throw new IllegalStateException(msg);
            }

            if (responseBody != null && responseBody.has("messageId")) {
                String messageId = responseBody.get("messageId").asText();
                log.info("Email accepté par Brevo → to={}, messageId={}", to, messageId);
                return messageId;
            }

            log.warn("Brevo a répondu HTTP {} sans messageId pour {} : {}",
                    status.value(), to, responseBody);
            return null;

        } catch (HttpStatusCodeException ex) {
            // Corps d'erreur Brevo (JSON) — cause la plus fréquente : sender non vérifié, clé invalide, quota
            String responseBody = ex.getResponseBodyAsString();
            String msg = String.format(
                    "Échec API Brevo HTTP %s vers %s | from=%s | body=%s",
                    ex.getStatusCode().value(), to, fromAddress,
                    responseBody != null && !responseBody.isBlank() ? responseBody : "(vide)");
            log.error(msg, ex);
            throw new IllegalStateException(msg, ex);

        } catch (RestClientException ex) {
            String msg = String.format(
                    "Erreur réseau / client lors de l'appel Brevo vers %s : %s",
                    to, ex.getMessage());
            log.error(msg, ex);
            throw new IllegalStateException(msg, ex);
        }
    }
}
