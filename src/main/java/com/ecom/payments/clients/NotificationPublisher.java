package com.ecom.payments.clients;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Publishes events to the Notification Service. Best effort: a failure is logged and never breaks the
 * caller's request. The envelope matches the planned Kafka message format.
 * TODO: publish to Kafka instead, for durable delivery (HLD 3.4 / 3.5 / 3.6)
 */
@Component
public class NotificationPublisher {
    private static final Logger log = LoggerFactory.getLogger(NotificationPublisher.class);

    private final RestTemplate restTemplate;
    private final String notificationServiceBaseUrl;
    private final String internalApiToken;

    public NotificationPublisher(@Value("${notificationservice.base-url:}") String notificationServiceBaseUrl,
                                 @Value("${internal.api-token:}") String internalApiToken) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        // Short timeouts: a slow Notification Service must not slow down the caller
        requestFactory.setConnectTimeout(2000);
        requestFactory.setReadTimeout(3000);
        this.restTemplate = new RestTemplate(requestFactory);
        this.notificationServiceBaseUrl = notificationServiceBaseUrl;
        this.internalApiToken = internalApiToken;
    }

    /**
     * @param eventId unique per event; use a stable id (e.g. "user-1-registered") so a repeat is ignored
     */
    public void publish(String eventId, String type, Map<String, Object> data) {
        if (notificationServiceBaseUrl.isBlank()) {
            log.debug("Notification Service not configured; dropping {} event {}", type, eventId);
            return;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", internalApiToken);
        Map<String, Object> event = Map.of(
                "eventId", eventId,
                "type", type,
                "occurredAt", System.currentTimeMillis(),
                "data", data);
        try {
            restTemplate.postForEntity(notificationServiceBaseUrl + "/internal/api/v1/events",
                    new HttpEntity<>(event, headers), String.class);
        } catch (RestClientException e) {
            log.warn("Could not publish {} event {}: {}", type, eventId, e.getMessage());
        }
    }
}
