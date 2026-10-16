package com.ecom.payments.services;

import com.ecom.payments.exceptions.InvalidWebhookSignatureException;
import com.ecom.payments.models.PaymentStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WebhookServiceTest {
    private static final String SECRET = "test_webhook_secret";
    private static final String PAID = "{\"event\":\"payment_link.paid\",\"payload\":{\"payment_link\":{\"entity\":"
            + "{\"id\":\"plink_1\"}},\"payment\":{\"entity\":{\"id\":\"pay_A\"}}}}";

    private PaymentService paymentService;
    private WebhookService webhook;

    @BeforeEach
    void setUp() {
        paymentService = mock(PaymentService.class);
        webhook = new WebhookService(paymentService, new ObjectMapper(), SECRET);
    }

    static String sign(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void validSignatureAppliesTheStatus() throws Exception {
        webhook.handle(PAID, sign(PAID, SECRET));
        verify(paymentService).applyGatewayStatus("plink_1", PaymentStatus.PAID, "pay_A");
    }

    @Test
    void signatureHexIsCaseInsensitive() throws Exception {
        webhook.handle(PAID, sign(PAID, SECRET).toUpperCase());
        verify(paymentService).applyGatewayStatus("plink_1", PaymentStatus.PAID, "pay_A");
    }

    @Test
    void expiredAndCancelledEventsMapToStatuses() throws Exception {
        String expired = "{\"event\":\"payment_link.expired\",\"payload\":{\"payment_link\":{\"entity\":{\"id\":\"plink_1\"}}}}";
        String cancelled = "{\"event\":\"payment_link.cancelled\",\"payload\":{\"payment_link\":{\"entity\":{\"id\":\"plink_1\"}}}}";
        webhook.handle(expired, sign(expired, SECRET));
        webhook.handle(cancelled, sign(cancelled, SECRET));
        verify(paymentService).applyGatewayStatus("plink_1", PaymentStatus.EXPIRED, null);
        verify(paymentService).applyGatewayStatus("plink_1", PaymentStatus.CANCELLED, null);
    }

    @Test
    void badOrMissingSignaturesAreRejected() throws Exception {
        assertThatThrownBy(() -> webhook.handle(PAID, null)).isInstanceOf(InvalidWebhookSignatureException.class);
        assertThatThrownBy(() -> webhook.handle(PAID, "deadbeef")).isInstanceOf(InvalidWebhookSignatureException.class);
        assertThatThrownBy(() -> webhook.handle(PAID, "not-hex")).isInstanceOf(InvalidWebhookSignatureException.class);
        assertThatThrownBy(() -> webhook.handle(PAID, sign(PAID, "wrong-secret")))
                .isInstanceOf(InvalidWebhookSignatureException.class);
        // Signed body tampered with afterwards
        assertThatThrownBy(() -> webhook.handle(PAID.replace("pay_A", "pay_X"), sign(PAID, SECRET)))
                .isInstanceOf(InvalidWebhookSignatureException.class);
        verifyNoInteractions(paymentService);
    }

    @Test
    void missingSecretRejectsEverything() throws Exception {
        WebhookService noSecret = new WebhookService(paymentService, new ObjectMapper(), "");
        assertThatThrownBy(() -> noSecret.handle(PAID, sign(PAID, "anything")))
                .isInstanceOf(InvalidWebhookSignatureException.class).hasMessageContaining("not configured");
        verifyNoInteractions(paymentService);
    }

    @Test
    void unrelatedEventsAreIgnored() throws Exception {
        String other = "{\"event\":\"payment.captured\",\"payload\":{}}";
        webhook.handle(other, sign(other, SECRET));
        verify(paymentService, never()).applyGatewayStatus(any(), any(), any());
    }
}
