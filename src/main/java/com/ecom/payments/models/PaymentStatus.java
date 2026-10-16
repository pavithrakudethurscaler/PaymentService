package com.ecom.payments.models;

public enum PaymentStatus {
    /** Record created, payment link not generated yet */
    INITIATED,
    /** Payment link generated, waiting for the customer to pay */
    LINK_CREATED,
    PAID,
    EXPIRED,
    CANCELLED,
    /** The gateway could not create the payment link */
    FAILED
}
