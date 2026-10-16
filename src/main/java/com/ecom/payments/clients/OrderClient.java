package com.ecom.payments.clients;

import com.ecom.payments.exceptions.PaymentNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class OrderClient {
    private final RestTemplate restTemplate;
    private final String orderServiceBaseUrl;
    private final String internalApiToken;

    public OrderClient(RestTemplate restTemplate,
                       @Value("${orderservice.base-url}") String orderServiceBaseUrl,
                       @Value("${internal.api-token:}") String internalApiToken) {
        this.restTemplate = restTemplate;
        this.orderServiceBaseUrl = orderServiceBaseUrl;
        this.internalApiToken = internalApiToken;
    }

    /**
     * Fetches the order as the customer, by forwarding their bearer token. The Order Service only returns
     * the customer's own orders, so this also proves the order belongs to them.
     */
    public OrderDto getOrderForUser(String orderId, String bearerToken) throws PaymentNotFoundException {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearerToken);
        try {
            OrderDto order = restTemplate.exchange(orderServiceBaseUrl + "/api/v1/orders/{id}", HttpMethod.GET,
                    new HttpEntity<>(headers), OrderDto.class, orderId).getBody();
            if (order == null) {
                throw new PaymentNotFoundException("Order not found: " + orderId);
            }
            return order;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND || e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                throw new PaymentNotFoundException("Order not found: " + orderId);
            }
            throw new OrderServiceException("Order Service rejected the request", e);
        } catch (RestClientException e) {
            throw new OrderServiceException("Order Service is unavailable", e);
        }
    }

    /**
     * Tells the Order Service the order is paid. TODO: replace with a PaymentCompleted Kafka event (HLD 3.5)
     *
     * @return true if the order accepted the payment; false if the Order Service permanently rejected it
     *         (4xx, e.g. the order was already paid by another payment or was cancelled)
     * @throws OrderServiceException for temporary failures (5xx, connection errors, wrong internal token),
     *         so the caller can retry
     */
    public boolean notifyPaymentCompleted(String orderId, String paymentId, long amount) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", internalApiToken);
        try {
            restTemplate.exchange(orderServiceBaseUrl + "/internal/api/v1/orders/{id}/payment-completed",
                    HttpMethod.POST, new HttpEntity<>(Map.of("paymentId", paymentId, "amount", amount), headers),
                    Void.class, orderId);
            return true;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                // Misconfigured INTERNAL_API_TOKEN: fixable, so keep retrying rather than refunding
                throw new OrderServiceException("Order Service rejected the internal API token", e);
            }
            return false;
        } catch (RestClientException e) {
            throw new OrderServiceException("Could not notify the Order Service about payment " + paymentId, e);
        }
    }
}
