package com.ecom.payments.clients;

import com.ecom.payments.exceptions.PaymentNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class OrderClientTest {
    private MockRestServiceServer server;
    private OrderClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new OrderClient(restTemplate, "http://orders", "secret");
    }

    private void expectNotify(org.springframework.test.web.client.ResponseCreator response) {
        server.expect(requestTo("http://orders/internal/api/v1/orders/1/payment-completed"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", "secret"))
                .andExpect(jsonPath("$.paymentId").value("5"))
                .andExpect(jsonPath("$.amount").value(1000))
                .andRespond(response);
    }

    @Test
    void acceptedPayment() {
        expectNotify(withSuccess());
        assertThat(client.notifyPaymentCompleted("1", "5", 1000)).isTrue();
        server.verify();
    }

    @Test
    void conflictOrBadRequestIsAPermanentRejection() {
        expectNotify(withStatus(HttpStatus.CONFLICT));
        assertThat(client.notifyPaymentCompleted("1", "5", 1000)).isFalse();
        server.reset();
        expectNotify(withBadRequest());
        assertThat(client.notifyPaymentCompleted("1", "5", 1000)).isFalse();
    }

    @Test
    void unauthorizedAndServerErrorsAreRetryable() {
        expectNotify(withUnauthorizedRequest());
        assertThatThrownBy(() -> client.notifyPaymentCompleted("1", "5", 1000)).isInstanceOf(OrderServiceException.class);
        server.reset();
        expectNotify(withServerError());
        assertThatThrownBy(() -> client.notifyPaymentCompleted("1", "5", 1000)).isInstanceOf(OrderServiceException.class);
    }

    @Test
    void getOrderForwardsTheCustomersTokenAndMapsNotFound() throws Exception {
        server.expect(requestTo("http://orders/api/v1/orders/1"))
                .andExpect(header("Authorization", "Bearer tok"))
                .andRespond(withSuccess("{\"id\":1,\"status\":\"CREATED\",\"totalAmount\":1000,\"extra\":true}",
                        MediaType.APPLICATION_JSON));
        assertThat(client.getOrderForUser("1", "tok").getTotalAmount()).isEqualTo(1000);

        server.reset();
        server.expect(requestTo("http://orders/api/v1/orders/2")).andRespond(withResourceNotFound());
        assertThatThrownBy(() -> client.getOrderForUser("2", "tok")).isInstanceOf(PaymentNotFoundException.class);
    }
}
