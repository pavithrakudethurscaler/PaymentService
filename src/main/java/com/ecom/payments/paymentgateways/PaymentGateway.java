package com.ecom.payments.paymentgateways;

public interface PaymentGateway {
    /** Short name stored on each payment, e.g. "razorpay" */
    String name();

    PaymentLinkResult createPaymentLink(PaymentLinkRequest request);
}
