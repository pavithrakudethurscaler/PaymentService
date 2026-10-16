package com.ecom.payments.models;

import com.ecom.common.models.JpaBaseModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class Payment extends JpaBaseModel {
    @Column(nullable = false)
    private String orderId;
    /** Id of the paying user in the User Service */
    private Long userId;
    /** Amount in the smallest currency unit (paise for INR) */
    private long amount;
    private String currency;
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;
    /** Gateway that handled this payment, e.g. "razorpay" or "mock" */
    private String gateway;
    @Column(unique = true)
    private String gatewayLinkId;
    private String gatewayPaymentId;
    private String paymentLink;
    private long expiresAt;
    private long createdAt;
    private Long paidAt;
}
