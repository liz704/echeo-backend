package com.echeo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client HTTP minimal de l'API Faroty Pay :
 *  - POST /payments/api/v1/payment-sessions            -> crée une session, renvoie sessionToken + sessionUrl
 *  - GET  /payments/api/v1/transactions/session/{t}/status -> état de la transaction
 * Configuration par variables d'environnement (voir application.yml, section echeo.faroty).
 */
@Service
public class FarotyPayClient {

    public record Session(String sessionToken, String sessionUrl) {
    }

    /** status : PENDING / SUCCESS / FAILED (déduit de successful/failed). */
    public record TxStatus(String status, boolean successful, boolean failed, String message) {
    }

    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Value("${echeo.faroty.base-url:https://api-pay-prod.faroty.me}")
    private String baseUrl;
    @Value("${echeo.faroty.wallet-id:}")
    private String walletId;
    @Value("${echeo.faroty.api-key:}")
    private String apiKey;
    @Value("${echeo.faroty.api-key-header:Authorization}")
    private String apiKeyHeader;
    @Value("${echeo.faroty.currency:XAF}")
    private String currency;

    public FarotyPayClient(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public boolean isConfigured() {
        return walletId != null && !walletId.isBlank();
    }

    private HttpRequest.Builder base(String path) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl.replaceAll("/+$", "") + path))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");
        if (apiKey != null && !apiKey.isBlank()) {
            if ("Authorization".equalsIgnoreCase(apiKeyHeader) && !apiKey.toLowerCase().startsWith("bearer ")) {
                b.header("Authorization", "Bearer " + apiKey);
            } else {
                b.header(apiKeyHeader, apiKey);
            }
        }
        return b;
    }

    public Session createSession(BigDecimal amount, String successUrl, String cancelUrl, String ip) {
        if (!isConfigured()) {
            throw new IllegalStateException("Paiement en ligne non configuré (FAROTY_WALLET_ID manquant).");
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("walletId", walletId);
            body.put("currencyCode", currency);
            body.put("cancelUrl", cancelUrl);
            body.put("successUrl", successUrl);
            body.put("type", "DEPOSIT");
            body.put("ipAddress", ip == null || ip.isBlank() ? "0.0.0.0" : ip);
            body.put("amount", amount.stripTrailingZeros().scale() <= 0 ? (Object) amount.longValue() : (Object) amount);
            HttpRequest req = base("/payments/api/v1/payment-sessions")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode root = mapper.readTree(res.body());
            if (res.statusCode() / 100 != 2 || !root.path("success").asBoolean(true)) {
                throw new IllegalStateException("Faroty Pay a refusé la session : " + root.path("message").asText(res.body()));
            }
            JsonNode data = root.path("data");
            String token = data.path("sessionToken").asText(null);
            String url = data.path("sessionUrl").asText(null);
            if (token == null || url == null) {
                throw new IllegalStateException("Réponse Faroty Pay inattendue.");
            }
            return new Session(token, url);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Faroty Pay injoignable : " + e.getMessage(), e);
        }
    }

    public TxStatus getStatus(String sessionToken) {
        try {
            HttpRequest req = base("/payments/api/v1/transactions/session/" + sessionToken + "/status").GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode root = mapper.readTree(res.body());
            if (res.statusCode() / 100 != 2) {
                throw new IllegalStateException("HTTP " + res.statusCode());
            }
            JsonNode d = root.path("data");
            boolean ok = d.path("successful").asBoolean(false);
            boolean ko = d.path("failed").asBoolean(false);
            String status = ok ? "SUCCESS" : ko ? "FAILED" : "PENDING";
            return new TxStatus(status, ok, ko, d.path("message").asText(""));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Faroty Pay injoignable : " + e.getMessage(), e);
        }
    }
}
