package com.ecom.payments.paymentgateways;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Local-development gateway that returns fake links without calling any provider.
 * Completing a mock payment is simulated by posting a Razorpay-format webhook signed with the webhook secret.
 */
@Component
@ConditionalOnProperty(name = "payment.gateway", havingValue = "mock", matchIfMissing = true)
public class MockPaymentGateway implements PaymentGateway {
    @Override
    public String name() {
        return "mock";
    }

    @Override
    public PaymentLinkResult createPaymentLink(PaymentLinkRequest request) {
        String linkId = "plink_mock_" + UUID.randomUUID().toString().replace("-", "");
        return new PaymentLinkResult(linkId, "https://mock-payments.local/pay/" + linkId);
    }
}
