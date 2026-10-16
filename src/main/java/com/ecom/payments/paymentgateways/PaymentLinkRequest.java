package com.ecom.payments.paymentgateways;

/**
 * What a gateway needs to create a payment link.
 *
 * @param referenceId unique per link; we use our own payment id so retries for the same order do not collide
 */
public record PaymentLinkRequest(String referenceId, String orderId, long amount, String currency,
                                 String customerName, String customerEmail, String customerPhone,
                                 long expiresAtEpochSeconds, String callbackUrl) {
}
