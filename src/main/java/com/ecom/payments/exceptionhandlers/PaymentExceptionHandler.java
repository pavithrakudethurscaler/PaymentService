package com.ecom.payments.exceptionhandlers;

import com.ecom.common.dtos.ErrorDto;
import com.ecom.payments.auth.AuthServiceUnavailableException;
import com.ecom.payments.auth.UnauthorizedException;
import com.ecom.payments.clients.OrderServiceException;
import com.ecom.payments.exceptions.InvalidWebhookSignatureException;
import com.ecom.payments.exceptions.OrderAlreadyPaidException;
import com.ecom.payments.exceptions.PaymentNotFoundException;
import com.ecom.payments.paymentgateways.PaymentGatewayException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class PaymentExceptionHandler {
    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ErrorDto> handlePaymentNotFound(PaymentNotFoundException e) {
        return new ResponseEntity<>(new ErrorDto(e.getMessage()), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(OrderAlreadyPaidException.class)
    public ResponseEntity<ErrorDto> handleOrderAlreadyPaid(OrderAlreadyPaidException e) {
        return new ResponseEntity<>(new ErrorDto(e.getMessage()), HttpStatus.CONFLICT);
    }

    @ExceptionHandler({UnauthorizedException.class, InvalidWebhookSignatureException.class})
    public ResponseEntity<ErrorDto> handleUnauthorized(Exception e) {
        return new ResponseEntity<>(new ErrorDto(e.getMessage()), HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(PaymentGatewayException.class)
    public ResponseEntity<ErrorDto> handlePaymentGateway(PaymentGatewayException e) {
        return new ResponseEntity<>(new ErrorDto(e.getMessage()), HttpStatus.BAD_GATEWAY);
    }

    @ExceptionHandler({AuthServiceUnavailableException.class, OrderServiceException.class})
    public ResponseEntity<ErrorDto> handleServiceUnavailable(RuntimeException e) {
        return new ResponseEntity<>(new ErrorDto(e.getMessage()), HttpStatus.SERVICE_UNAVAILABLE);
    }
}
