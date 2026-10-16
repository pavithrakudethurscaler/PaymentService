package com.ecom.payments.clients;

public class OrderServiceException extends RuntimeException {
    public OrderServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
