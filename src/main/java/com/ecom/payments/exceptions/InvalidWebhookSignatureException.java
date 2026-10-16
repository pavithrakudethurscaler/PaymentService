package com.ecom.payments.exceptions;

public class InvalidWebhookSignatureException extends Exception {
    public InvalidWebhookSignatureException(String message) {
        super(message);
    }
}
