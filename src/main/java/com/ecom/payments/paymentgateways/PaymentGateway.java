package com.ecom.payments.paymentgateways;

public interface PaymentGateway {
    /** Short name stored on each payment, e.g. "razorpay" */
    String name();

    PaymentLinkResult createPaymentLink(PaymentLinkRequest request);

    /** Cancels an unpaid link so the customer can no longer pay it */
    void cancelPaymentLink(String linkId);
}
