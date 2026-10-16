package com.ecom.payments.configs;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "payment.gateway", havingValue = "razorpay")
public class RazorpayClientConfig {
    @Bean
    public RazorpayClient razorpayClient(@Value("${razorpay.key.id}") String keyId,
                                         @Value("${razorpay.key.secret}") String keySecret) throws RazorpayException {
        if (keyId.isBlank() || keySecret.isBlank()) {
            throw new IllegalStateException("payment.gateway=razorpay requires RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET");
        }
        return new RazorpayClient(keyId, keySecret);
    }
}
