package com.ecom.payments.repos;

import com.ecom.payments.models.Payment;
import com.ecom.payments.models.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepo extends JpaRepository<Payment, Long> {
    Optional<Payment> findByIdAndUserId(Long id, Long userId);

    List<Payment> findByOrderIdAndUserId(String orderId, Long userId);

    boolean existsByOrderIdAndStatus(String orderId, PaymentStatus status);

    Optional<Payment> findByGatewayLinkId(String gatewayLinkId);
}
