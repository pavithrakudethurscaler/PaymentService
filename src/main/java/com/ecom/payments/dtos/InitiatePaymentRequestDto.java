package com.ecom.payments.dtos;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InitiatePaymentRequestDto {
    private String orderId;
    /** Amount in the smallest currency unit (paise for INR) */
    private Long amount;
    /** Optional; enables SMS notification from the gateway */
    private String phoneNumber;
}
