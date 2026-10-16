package com.ecom.payments.models;

public enum PaymentStatus {
    /** Record created, payment link not generated yet */
    INITIATED,
    /** Payment link generated, waiting for the customer to pay */
    LINK_CREATED,
    PAID,
    /**
     * The customer paid, but the Order Service rejected the payment because the order was no longer payable
     * (already paid by another link, or cancelled). The money must be refunded.
     */
    REFUND_PENDING,
    EXPIRED,
    CANCELLED,
    /** The gateway could not create the payment link */
    FAILED;

    /** Money was received for this payment; its status must never change again */
    public boolean isMoneyReceived() {
        return this == PAID || this == REFUND_PENDING;
    }
}
