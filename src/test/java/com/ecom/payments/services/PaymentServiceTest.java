package com.ecom.payments.services;

import com.ecom.payments.auth.AuthUserDto;
import com.ecom.payments.clients.NotificationPublisher;
import com.ecom.payments.clients.OrderClient;
import com.ecom.payments.clients.OrderDto;
import com.ecom.payments.clients.OrderServiceException;
import com.ecom.payments.exceptions.OrderAlreadyPaidException;
import com.ecom.payments.exceptions.PaymentNotFoundException;
import com.ecom.payments.models.Payment;
import com.ecom.payments.models.PaymentStatus;
import com.ecom.payments.paymentgateways.PaymentGateway;
import com.ecom.payments.paymentgateways.PaymentGatewayException;
import com.ecom.payments.paymentgateways.PaymentLinkRequest;
import com.ecom.payments.paymentgateways.PaymentLinkResult;
import com.ecom.payments.repos.PaymentRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentServiceTest {
    private PaymentRepo paymentRepo;
    private PaymentGateway gateway;
    private OrderClient orderClient;
    private NotificationPublisher publisher;
    private PaymentService service;
    private final AtomicLong ids = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        paymentRepo = mock(PaymentRepo.class);
        gateway = mock(PaymentGateway.class);
        orderClient = mock(OrderClient.class);
        publisher = mock(NotificationPublisher.class);
        service = new PaymentService(paymentRepo, gateway, orderClient, publisher, "INR", 30, "http://cb");
        when(gateway.name()).thenReturn("mock");
        when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(ids.getAndIncrement());
            }
            return p;
        });
    }

    private static AuthUserDto alice() {
        AuthUserDto u = new AuthUserDto();
        u.setId(7L);
        u.setName("Alice");
        u.setEmail("alice@x.com");
        return u;
    }

    private static OrderDto order(String status, long total) {
        OrderDto o = new OrderDto();
        o.setId(1L);
        o.setStatus(status);
        o.setTotalAmount(total);
        return o;
    }

    private Payment payment(Long id, PaymentStatus status, String linkId) {
        Payment p = new Payment();
        p.setId(id);
        p.setOrderId("1");
        p.setUserId(7L);
        p.setAmount(100028);
        p.setCurrency("INR");
        p.setStatus(status);
        p.setGatewayLinkId(linkId);
        when(paymentRepo.findByGatewayLinkId(linkId)).thenReturn(Optional.of(p));
        return p;
    }

    // ---- initiatePayment

    @Test
    void chargesTheOrderTotalFromTheOrderService() throws Exception {
        when(orderClient.getOrderForUser("1", "tok")).thenReturn(order("CREATED", 100028));
        when(gateway.createPaymentLink(any())).thenReturn(new PaymentLinkResult("plink_1", "https://pay/1"));

        Payment p = service.initiatePayment(alice(), "tok", " 1 ", "+91999");

        assertThat(p.getAmount()).isEqualTo(100028);
        assertThat(p.getOrderId()).isEqualTo("1");
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.LINK_CREATED);
        assertThat(p.getPaymentLink()).isEqualTo("https://pay/1");
        ArgumentCaptor<PaymentLinkRequest> req = ArgumentCaptor.forClass(PaymentLinkRequest.class);
        verify(gateway).createPaymentLink(req.capture());
        assertThat(req.getValue().amount()).isEqualTo(100028);
        assertThat(req.getValue().referenceId()).isEqualTo("payment_" + p.getId());
        assertThat(req.getValue().customerEmail()).isEqualTo("alice@x.com");
    }

    @Test
    void onlyOrdersAwaitingPaymentCanBePaid() throws Exception {
        when(orderClient.getOrderForUser("1", "tok")).thenReturn(order("PAID", 100));
        assertThatThrownBy(() -> service.initiatePayment(alice(), "tok", "1", null))
                .isInstanceOf(OrderAlreadyPaidException.class).hasMessageContaining("status PAID");
        when(orderClient.getOrderForUser("2", "tok")).thenReturn(order("CANCELLED", 100));
        assertThatThrownBy(() -> service.initiatePayment(alice(), "tok", "2", null))
                .isInstanceOf(OrderAlreadyPaidException.class);
        verify(gateway, never()).createPaymentLink(any());
    }

    @Test
    void someoneElsesOrderIsNotFound() throws Exception {
        when(orderClient.getOrderForUser("1", "tok")).thenThrow(new PaymentNotFoundException("Order not found: 1"));
        assertThatThrownBy(() -> service.initiatePayment(alice(), "tok", "1", null))
                .isInstanceOf(PaymentNotFoundException.class);
        assertThatThrownBy(() -> service.initiatePayment(alice(), "tok", " ", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void gatewayFailureIsRecordedAsFailed() throws Exception {
        when(orderClient.getOrderForUser("1", "tok")).thenReturn(order("CREATED", 500));
        when(gateway.createPaymentLink(any())).thenThrow(new PaymentGatewayException("down", null));

        assertThatThrownBy(() -> service.initiatePayment(alice(), "tok", "1", null))
                .isInstanceOf(PaymentGatewayException.class);
        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepo, atLeastOnce()).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    // ---- applyGatewayStatus

    @Test
    void paidNotifiesTheOrderSendsAReceiptAndCancelsOtherLinks() {
        Payment p = payment(1L, PaymentStatus.LINK_CREATED, "plink_1");
        Payment other = payment(2L, PaymentStatus.LINK_CREATED, "plink_2");
        when(paymentRepo.findByOrderIdAndStatus("1", PaymentStatus.LINK_CREATED)).thenReturn(List.of(p, other));
        when(orderClient.notifyPaymentCompleted("1", "1", 100028)).thenReturn(true);

        service.applyGatewayStatus("plink_1", PaymentStatus.PAID, "pay_A");

        assertThat(p.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(p.getGatewayPaymentId()).isEqualTo("pay_A");
        assertThat(p.getPaidAt()).isNotNull();
        verify(gateway).cancelPaymentLink("plink_2");
        verify(gateway, never()).cancelPaymentLink("plink_1");
        assertThat(other.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(publisher).publish(eq("payment-1-received"), eq("PAYMENT_RECEIVED"), anyMap());
    }

    @Test
    void rejectedByTheOrderBecomesRefundPendingWithoutThrowing() {
        Payment p = payment(2L, PaymentStatus.LINK_CREATED, "plink_2");
        when(orderClient.notifyPaymentCompleted("1", "2", 100028)).thenReturn(false);

        service.applyGatewayStatus("plink_2", PaymentStatus.PAID, "pay_B");

        assertThat(p.getStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);
        ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.forClass(Map.class);
        verify(publisher).publish(eq("payment-2-refund-pending"), eq("PAYMENT_REFUND_PENDING"), data.capture());
        assertThat(data.getValue()).containsEntry("amount", 100028L).containsEntry("userId", 7L);
        verify(publisher, never()).publish(any(), eq("PAYMENT_RECEIVED"), anyMap());
    }

    @Test
    void temporaryOrderServiceFailurePropagatesAndPaymentStaysPaid() {
        Payment p = payment(1L, PaymentStatus.LINK_CREATED, "plink_1");
        when(orderClient.notifyPaymentCompleted(any(), any(), anyLong()))
                .thenThrow(new OrderServiceException("down", null));

        assertThatThrownBy(() -> service.applyGatewayStatus("plink_1", PaymentStatus.PAID, "pay_A"))
                .isInstanceOf(OrderServiceException.class);
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.PAID);
        verifyNoInteractions(publisher);
    }

    @Test
    void replayOfPaidReNotifiesTheOrder() {
        Payment p = payment(1L, PaymentStatus.PAID, "plink_1");
        when(orderClient.notifyPaymentCompleted("1", "1", 100028)).thenReturn(true);

        service.applyGatewayStatus("plink_1", PaymentStatus.PAID, "pay_A");

        verify(orderClient).notifyPaymentCompleted("1", "1", 100028);
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void refundPendingIsFinal() {
        Payment p = payment(2L, PaymentStatus.REFUND_PENDING, "plink_2");
        service.applyGatewayStatus("plink_2", PaymentStatus.PAID, "pay_B");
        service.applyGatewayStatus("plink_2", PaymentStatus.EXPIRED, null);
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);
        verifyNoInteractions(orderClient, publisher);
    }

    @Test
    void lateExpiredAfterPaidIsIgnored() {
        Payment p = payment(1L, PaymentStatus.PAID, "plink_1");
        service.applyGatewayStatus("plink_1", PaymentStatus.EXPIRED, null);
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(paymentRepo, never()).save(any());
    }

    @Test
    void cancelledLinkThatIsPaidAnywayIsTreatedAsPaid() {
        Payment p = payment(2L, PaymentStatus.CANCELLED, "plink_2");
        when(orderClient.notifyPaymentCompleted("1", "2", 100028)).thenReturn(false);

        service.applyGatewayStatus("plink_2", PaymentStatus.PAID, "pay_B");

        verify(orderClient).notifyPaymentCompleted("1", "2", 100028);
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);
    }

    @Test
    void expiredMovesAnOpenLinkAndUnknownLinksAreIgnored() {
        Payment p = payment(1L, PaymentStatus.LINK_CREATED, "plink_1");
        service.applyGatewayStatus("plink_1", PaymentStatus.EXPIRED, null);
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.EXPIRED);

        when(paymentRepo.findByGatewayLinkId("nope")).thenReturn(Optional.empty());
        service.applyGatewayStatus("nope", PaymentStatus.PAID, "x");
        verifyNoInteractions(orderClient);
    }

    @Test
    void paymentsAreScopedToTheirOwner() throws Exception {
        when(paymentRepo.findByIdAndUserId(1L, 8L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getPayment(1L, 8L))
                .isInstanceOf(PaymentNotFoundException.class).hasMessage("Payment not found: 1");
    }
}
