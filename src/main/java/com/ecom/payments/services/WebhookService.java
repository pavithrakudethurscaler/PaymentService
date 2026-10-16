package com.ecom.payments.services;

import com.ecom.payments.exceptions.InvalidWebhookSignatureException;
import com.ecom.payments.models.PaymentStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/**
 * Handles Razorpay payment-link webhooks.
 * The X-Razorpay-Signature header is the hex HMAC-SHA256 of the raw request body, keyed with the webhook secret.
 */
@Service
public class WebhookService {
    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);
    private static final Map<String, PaymentStatus> EVENT_STATUS = Map.of(
            "payment_link.paid", PaymentStatus.PAID,
            "payment_link.expired", PaymentStatus.EXPIRED,
            "payment_link.cancelled", PaymentStatus.CANCELLED);

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;
    private final String webhookSecret;

    public WebhookService(PaymentService paymentService, ObjectMapper objectMapper,
                          @Value("${razorpay.webhook.secret:}") String webhookSecret) {
        this.paymentService = paymentService;
        this.objectMapper = objectMapper;
        this.webhookSecret = webhookSecret;
    }

    public void handle(String rawBody, String signature) throws InvalidWebhookSignatureException {
        verifySignature(rawBody, signature);

        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Webhook body is not valid JSON");
        }

        String event = root.path("event").asText();
        PaymentStatus newStatus = EVENT_STATUS.get(event);
        if (newStatus == null) {
            log.info("Ignoring webhook event {}", event);
            return;
        }

        String linkId = root.path("payload").path("payment_link").path("entity").path("id").asText(null);
        if (linkId == null) {
            throw new IllegalArgumentException("Webhook is missing payload.payment_link.entity.id");
        }
        String gatewayPaymentId = root.path("payload").path("payment").path("entity").path("id").asText(null);
        paymentService.applyGatewayStatus(linkId, newStatus, gatewayPaymentId);
    }

    private void verifySignature(String rawBody, String signature) throws InvalidWebhookSignatureException {
        if (webhookSecret.isBlank()) {
            // Fail closed: without a secret we cannot tell real gateway calls from forged ones
            throw new InvalidWebhookSignatureException("Webhook secret is not configured");
        }
        if (signature == null || signature.isBlank()) {
            throw new InvalidWebhookSignatureException("Missing X-Razorpay-Signature header");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            byte[] actual = HexFormat.of().parseHex(signature.trim().toLowerCase());
            if (!MessageDigest.isEqual(expected, actual)) {
                throw new InvalidWebhookSignatureException("Invalid webhook signature");
            }
        } catch (IllegalArgumentException e) {
            throw new InvalidWebhookSignatureException("Invalid webhook signature");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }
}
