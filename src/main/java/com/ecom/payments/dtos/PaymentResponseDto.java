package com.ecom.payments.dtos;

import com.ecom.payments.models.Payment;
import com.ecom.payments.models.PaymentStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PaymentResponseDto {
    private Long id;
    private String orderId;
    private long amount;
    private String currency;
    private PaymentStatus status;
    private String paymentLink;
    private long expiresAt;
    private Long paidAt;

    public static PaymentResponseDto from(Payment payment) {
        PaymentResponseDto responseDto = new PaymentResponseDto();
        responseDto.setId(payment.getId());
        responseDto.setOrderId(payment.getOrderId());
        responseDto.setAmount(payment.getAmount());
        responseDto.setCurrency(payment.getCurrency());
        responseDto.setStatus(payment.getStatus());
        responseDto.setPaymentLink(payment.getPaymentLink());
        responseDto.setExpiresAt(payment.getExpiresAt());
        responseDto.setPaidAt(payment.getPaidAt());
        return responseDto;
    }
}
