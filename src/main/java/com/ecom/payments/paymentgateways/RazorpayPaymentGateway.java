package com.ecom.payments.paymentgateways;

import com.razorpay.PaymentLink;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.json.JSONObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "payment.gateway", havingValue = "razorpay")
public class RazorpayPaymentGateway implements PaymentGateway {
    private final RazorpayClient razorpayClient;

    public RazorpayPaymentGateway(RazorpayClient razorpayClient) {
        this.razorpayClient = razorpayClient;
    }

    @Override
    public String name() {
        return "razorpay";
    }

    @Override
    public PaymentLinkResult createPaymentLink(PaymentLinkRequest request) {
        JSONObject paymentLinkRequest = new JSONObject();
        paymentLinkRequest.put("amount", request.amount());
        paymentLinkRequest.put("currency", request.currency());
        paymentLinkRequest.put("accept_partial", false);
        paymentLinkRequest.put("expire_by", request.expiresAtEpochSeconds());
        paymentLinkRequest.put("reference_id", request.referenceId());
        paymentLinkRequest.put("description", "Payment for order no " + request.orderId());

        JSONObject customer = new JSONObject();
        customer.put("name", request.customerName());
        customer.put("email", request.customerEmail());
        if (request.customerPhone() != null && !request.customerPhone().isBlank()) {
            customer.put("contact", request.customerPhone());
        }
        paymentLinkRequest.put("customer", customer);

        JSONObject notify = new JSONObject();
        notify.put("sms", request.customerPhone() != null && !request.customerPhone().isBlank());
        notify.put("email", true);
        paymentLinkRequest.put("notify", notify);
        paymentLinkRequest.put("reminder_enable", false);

        JSONObject notes = new JSONObject();
        notes.put("order_id", request.orderId());
        paymentLinkRequest.put("notes", notes);

        paymentLinkRequest.put("callback_url", request.callbackUrl());
        paymentLinkRequest.put("callback_method", "get");

        try {
            PaymentLink paymentLink = razorpayClient.paymentLink.create(paymentLinkRequest);
            return new PaymentLinkResult(paymentLink.get("id").toString(), paymentLink.get("short_url").toString());
        } catch (RazorpayException e) {
            throw new PaymentGatewayException("Razorpay could not create the payment link", e);
        }
    }
}
