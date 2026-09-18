package com.echeo.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
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

    private static final String BREVO_SEND_URL = "https://api.brevo.com/v3/smtp/email";

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${echeo.brevo.api-key}")
    private String brevoApiKey;

    @Value("${echeo.mail.from}")
    private String fromAddress;

    @Value("${echeo.mail.from-name:ÉCHÉO}")
    private String fromName;

    /**
     * Envoie un email et renvoie le messageId Brevo (nécessaire pour relier
     * plus tard l'événement de livraison webhook à ce NotificationLog).
     */
    public String send(String to, String subject, String body) {
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
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                BREVO_SEND_URL, HttpMethod.POST, request, JsonNode.class);

        JsonNode responseBody = response.getBody();
        if (responseBody != null && responseBody.has("messageId")) {
            return responseBody.get("messageId").asText();
        }
        return null;
    }
}
