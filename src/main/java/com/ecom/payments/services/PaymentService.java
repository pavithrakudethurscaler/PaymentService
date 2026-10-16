package com.ecom.payments.services;

import com.ecom.payments.auth.AuthUserDto;
import com.ecom.payments.clients.OrderClient;
import com.ecom.payments.clients.OrderDto;
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
    private final OrderClient orderClient;
    private final String currency;
    private final long linkExpiryMinutes;
    private final String callbackUrl;

    public PaymentService(PaymentRepo paymentRepo, PaymentGateway paymentGateway, OrderClient orderClient,
                          @Value("${payment.currency:INR}") String currency,
                          @Value("${payment.link.expiry-minutes:30}") long linkExpiryMinutes,
                          @Value("${payment.callback-url}") String callbackUrl) {
        this.paymentRepo = paymentRepo;
        this.paymentGateway = paymentGateway;
        this.orderClient = orderClient;
        this.currency = currency;
        this.linkExpiryMinutes = linkExpiryMinutes;
        this.callbackUrl = callbackUrl;
    }

    /*
     1. Fetch the order from the Order Service as the customer (proves ownership, gives the real total)
     2. Check the order is still waiting for payment
     3. Save a payment record for the order total
     4. Ask the gateway for a payment link
     5. Return the link to the customer
     */
    public Payment initiatePayment(AuthUserDto user, String bearerToken, String orderId, String phoneNumber)
            throws OrderAlreadyPaidException, PaymentNotFoundException {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId is required");
        }
        orderId = orderId.trim();
        OrderDto order = orderClient.getOrderForUser(orderId, bearerToken);
        if (!"CREATED".equals(order.getStatus()) || paymentRepo.existsByOrderIdAndStatus(orderId, PaymentStatus.PAID)) {
            throw new OrderAlreadyPaidException("Order " + orderId + " is not awaiting payment (status "
                    + order.getStatus() + ")");
        }
        long amount = order.getTotalAmount();

        long now = System.currentTimeMillis();
        Payment payment = new Payment();
        payment.setOrderId(orderId);
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
     * because gateways retry webhooks. Once money is received (PAID / REFUND_PENDING) the payment is never
     * moved back to an unpaid status.
     * <p>
     * When a payment is PAID the Order Service is notified:
     * <ul>
     *   <li>accepted: the order's other open payment links are cancelled so they cannot be paid too</li>
     *   <li>permanently rejected (order already paid by another link, or cancelled): the payment becomes
     *       REFUND_PENDING and the webhook succeeds, so the gateway stops retrying</li>
     *   <li>temporary failure: the exception propagates, the webhook returns 503, and the gateway retries;
     *       the retry re-sends the notification (the Order Service treats repeats as a no-op)</li>
     * </ul>
     */
    public void applyGatewayStatus(String gatewayLinkId, PaymentStatus newStatus, String gatewayPaymentId) {
        Payment payment = paymentRepo.findByGatewayLinkId(gatewayLinkId).orElse(null);
        if (payment == null) {
            log.warn("Webhook for unknown payment link {}", gatewayLinkId);
            return;
        }
        if (payment.getStatus() == PaymentStatus.REFUND_PENDING) {
            return;
        }

        if (newStatus != PaymentStatus.PAID) {
            if (!payment.getStatus().isMoneyReceived() && payment.getStatus() != newStatus) {
                updateStatus(payment, newStatus);
            }
            return;
        }

        if (payment.getStatus() != PaymentStatus.PAID) {
            payment.setGatewayPaymentId(gatewayPaymentId);
            payment.setPaidAt(System.currentTimeMillis());
            updateStatus(payment, PaymentStatus.PAID);
        }

        boolean accepted = orderClient.notifyPaymentCompleted(
                payment.getOrderId(), String.valueOf(payment.getId()), payment.getAmount());
        if (!accepted) {
            updateStatus(payment, PaymentStatus.REFUND_PENDING);
            // TODO: issue the refund through the gateway automatically, and notify the customer
            log.error("REFUND REQUIRED: payment {} (gateway payment {}) for order {} was rejected by the Order Service",
                    payment.getId(), payment.getGatewayPaymentId(), payment.getOrderId());
            return;
        }
        cancelOtherOpenLinks(payment);
    }

    // Once an order is paid, close its other unpaid links so the customer cannot pay twice
    private void cancelOtherOpenLinks(Payment paidPayment) {
        for (Payment other : paymentRepo.findByOrderIdAndStatus(paidPayment.getOrderId(), PaymentStatus.LINK_CREATED)) {
            if (other.getId().equals(paidPayment.getId())) {
                continue;
            }
            try {
                paymentGateway.cancelPaymentLink(other.getGatewayLinkId());
                updateStatus(other, PaymentStatus.CANCELLED);
            } catch (PaymentGatewayException e) {
                // Best effort: if the link is paid anyway, the Order Service rejects it and it becomes REFUND_PENDING
                log.warn("Could not cancel payment link {} for order {}", other.getGatewayLinkId(), other.getOrderId(), e);
            }
        }
    }

    private void updateStatus(Payment payment, PaymentStatus status) {
        payment.setStatus(status);
        paymentRepo.save(payment);
        log.info("Payment {} for order {} is now {}", payment.getId(), payment.getOrderId(), status);
    }
}
