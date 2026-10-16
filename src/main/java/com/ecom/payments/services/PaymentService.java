package com.ecom.payments.services;

import com.ecom.payments.auth.AuthUserDto;
import com.ecom.payments.exceptions.OrderAlreadyPaidException;
import com.ecom.payments.exceptions.PaymentNotFoundException;
import com.ecom.payments.models.Payment;
import com.ecom.payments.models.PaymentStatus;
import com.ecom.payments.paymentgateways.PaymentGateway;
import com.ecom.payments.paymentgateways.PaymentGatewayException;
import com.ecom.payments.paymentgateways.PaymentLinkRequest;
import com.ecom.payments.paymentgateways.PaymentLinkResult;
import com.ecom.payments.repos.PaymentRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepo paymentRepo;
    private final PaymentGateway paymentGateway;
    private final String currency;
    private final long linkExpiryMinutes;
    private final String callbackUrl;

    public PaymentService(PaymentRepo paymentRepo, PaymentGateway paymentGateway,
                          @Value("${payment.currency:INR}") String currency,
                          @Value("${payment.link.expiry-minutes:30}") long linkExpiryMinutes,
                          @Value("${payment.callback-url}") String callbackUrl) {
        this.paymentRepo = paymentRepo;
        this.paymentGateway = paymentGateway;
        this.currency = currency;
        this.linkExpiryMinutes = linkExpiryMinutes;
        this.callbackUrl = callbackUrl;
    }

    /*
     1. Validate the request (TODO: fetch the order from the Order Service and verify the amount)
     2. Save a payment record
     3. Ask the gateway for a payment link
     4. Return the link to the customer
     */
    public Payment initiatePayment(AuthUserDto user, String orderId, Long amount, String phoneNumber)
            throws OrderAlreadyPaidException {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId is required");
        }
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("amount must be a positive number of paise");
        }
        if (paymentRepo.existsByOrderIdAndStatus(orderId, PaymentStatus.PAID)) {
            throw new OrderAlreadyPaidException("Order " + orderId + " is already paid");
        }

        long now = System.currentTimeMillis();
        Payment payment = new Payment();
        payment.setOrderId(orderId.trim());
        payment.setUserId(user.getId());
        payment.setAmount(amount);
        payment.setCurrency(currency);
        payment.setStatus(PaymentStatus.INITIATED);
        payment.setGateway(paymentGateway.name());
        payment.setCreatedAt(now);
        payment.setExpiresAt(now + linkExpiryMinutes * 60_000);
        payment = paymentRepo.save(payment);

        try {
            PaymentLinkResult link = paymentGateway.createPaymentLink(new PaymentLinkRequest(
                    "payment_" + payment.getId(), payment.getOrderId(), amount, currency,
                    user.getName(), user.getEmail(), phoneNumber,
                    payment.getExpiresAt() / 1000, callbackUrl));
            payment.setGatewayLinkId(link.linkId());
            payment.setPaymentLink(link.url());
            payment.setStatus(PaymentStatus.LINK_CREATED);
        } catch (PaymentGatewayException e) {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepo.save(payment);
            throw e;
        }
        return paymentRepo.save(payment);
    }

    public Payment getPayment(Long id, Long userId) throws PaymentNotFoundException {
        // Users can only see their own payments; others get the same 404 as a missing payment
        return paymentRepo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + id));
    }

    public List<Payment> getPaymentsForOrder(String orderId, Long userId) {
        return paymentRepo.findByOrderIdAndUserId(orderId, userId);
    }

    /**
     * Applies a status change reported by the gateway. Safe to call repeatedly with the same event,
     * because gateways retry webhooks. A PAID payment never changes again.
     */
    public void applyGatewayStatus(String gatewayLinkId, PaymentStatus newStatus, String gatewayPaymentId) {
        Payment payment = paymentRepo.findByGatewayLinkId(gatewayLinkId).orElse(null);
        if (payment == null) {
            log.warn("Webhook for unknown payment link {}", gatewayLinkId);
            return;
        }
        if (payment.getStatus() == PaymentStatus.PAID || payment.getStatus() == newStatus) {
            return;
        }

        payment.setStatus(newStatus);
        if (newStatus == PaymentStatus.PAID) {
            payment.setGatewayPaymentId(gatewayPaymentId);
            payment.setPaidAt(System.currentTimeMillis());
            // TODO: publish a PaymentCompleted Kafka event for the Order Service (HLD 3.5)
        }
        paymentRepo.save(payment);
        log.info("Payment {} for order {} is now {}", payment.getId(), payment.getOrderId(), newStatus);
    }
}
