package com.ecom.payments.dtos;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InitiatePaymentRequestDto {
    /** Order Service order id; the amount charged is that order's total */
    private String orderId;
    /** Optional; enables SMS notification from the gateway */
    private String phoneNumber;
}
