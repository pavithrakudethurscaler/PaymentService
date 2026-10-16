package com.ecom.payments.paymentgateways;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class MockPaymentGatewayTest {
    @Test
    void createsUniqueFakeLinks() {
        MockPaymentGateway gateway = new MockPaymentGateway();
        PaymentLinkRequest request = new PaymentLinkRequest("payment_1", "1", 1000, "INR", "A", "a@x.com", null, 0, "cb");
        PaymentLinkResult a = gateway.createPaymentLink(request);
        PaymentLinkResult b = gateway.createPaymentLink(request);
        assertThat(a.linkId()).startsWith("plink_mock_").isNotEqualTo(b.linkId());
        assertThat(a.url()).endsWith(a.linkId());
        assertThat(gateway.name()).isEqualTo("mock");
        assertThatCode(() -> gateway.cancelPaymentLink(a.linkId())).doesNotThrowAnyException();
    }
}
