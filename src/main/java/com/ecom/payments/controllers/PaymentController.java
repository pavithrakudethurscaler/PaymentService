package com.ecom.payments.controllers;

import com.ecom.payments.auth.AuthInterceptor;
import com.ecom.payments.auth.AuthUserDto;
import com.ecom.payments.dtos.InitiatePaymentRequestDto;
import com.ecom.payments.dtos.PaymentResponseDto;
import com.ecom.payments.exceptions.InvalidWebhookSignatureException;
import com.ecom.payments.exceptions.OrderAlreadyPaidException;
import com.ecom.payments.exceptions.PaymentNotFoundException;
import com.ecom.payments.models.Payment;
import com.ecom.payments.services.PaymentService;
import com.ecom.payments.services.WebhookService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    PaymentService paymentService;
    WebhookService webhookService;

    PaymentController(PaymentService paymentService, WebhookService webhookService) {
        this.paymentService = paymentService;
        this.webhookService = webhookService;
    }

    @PostMapping
    public ResponseEntity<PaymentResponseDto> initiatePayment(
            @RequestBody InitiatePaymentRequestDto initiatePaymentRequestDto,
            @RequestAttribute(AuthInterceptor.AUTH_USER_ATTRIBUTE) AuthUserDto user) throws OrderAlreadyPaidException {
        Payment payment = paymentService.initiatePayment(user,
                initiatePaymentRequestDto.getOrderId(),
                initiatePaymentRequestDto.getAmount(),
                initiatePaymentRequestDto.getPhoneNumber());
        return new ResponseEntity<>(PaymentResponseDto.from(payment), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponseDto> getPayment(
            @PathVariable Long id,
            @RequestAttribute(AuthInterceptor.AUTH_USER_ATTRIBUTE) AuthUserDto user) throws PaymentNotFoundException {
        return new ResponseEntity<>(PaymentResponseDto.from(paymentService.getPayment(id, user.getId())), HttpStatus.OK);
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<PaymentResponseDto>> getPaymentsForOrder(
            @PathVariable String orderId,
            @RequestAttribute(AuthInterceptor.AUTH_USER_ATTRIBUTE) AuthUserDto user) {
        List<PaymentResponseDto> payments = paymentService.getPaymentsForOrder(orderId, user.getId()).stream()
                .map(PaymentResponseDto::from)
                .toList();
        return new ResponseEntity<>(payments, HttpStatus.OK);
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> listenToWebhook(
            @RequestBody String webhookEvent,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature)
            throws InvalidWebhookSignatureException {
        webhookService.handle(webhookEvent, signature);
        return new ResponseEntity<>("OK", HttpStatus.OK);
    }
}
