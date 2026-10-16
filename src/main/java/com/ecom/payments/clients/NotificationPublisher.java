package com.ecom.payments.clients;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Publishes this service's domain events (HLD 3.4 / 3.5). By default to the Kafka topic {@code events.topic}, keyed by
 * user id so one user's events stay in order; the Notification Service consumes the topic. With
 * {@code events.transport=rest} the event is posted to the Notification Service directly instead (fallback when
 * no broker is available).
 * <p>
 * Best effort: a failure is logged and never breaks the caller's request.
 */
@Component
public class NotificationPublisher {
    private static final Logger log = LoggerFactory.getLogger(NotificationPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String transport;
    private final String topic;
    private final RestTemplate restTemplate;
    private final String notificationServiceBaseUrl;
    private final String internalApiToken;

    public NotificationPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper,
                                 @Value("${events.transport:kafka}") String transport,
                                 @Value("${events.topic}") String topic,
                                 @Value("${notificationservice.base-url:}") String notificationServiceBaseUrl,
                                 @Value("${internal.api-token:}") String internalApiToken) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.transport = transport;
        this.topic = topic;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        // Short timeouts: a slow Notification Service must not slow down the caller
        requestFactory.setConnectTimeout(2000);
        requestFactory.setReadTimeout(3000);
        this.restTemplate = new RestTemplate(requestFactory);
        this.notificationServiceBaseUrl = notificationServiceBaseUrl;
        this.internalApiToken = internalApiToken;
    }

    /**
     * @param eventId unique per event; use a stable id (e.g. "user-1-registered") so a repeat is ignored downstream
     */
    public void publish(String eventId, String type, Map<String, Object> data) {
        Map<String, Object> event = Map.of(
                "eventId", eventId,
                "type", type,
                "occurredAt", System.currentTimeMillis(),
                "data", data);
        if ("rest".equals(transport)) {
            publishRest(eventId, type, event);
        } else {
            publishKafka(eventId, type, event, String.valueOf(data.get("userId")));
        }
    }

    private void publishKafka(String eventId, String type, Map<String, Object> event, String key) {
        try {
            kafkaTemplate.send(topic, key, objectMapper.writeValueAsString(event))
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            log.warn("Could not publish {} event {} to {}: {}", type, eventId, topic, error.getMessage());
                        }
                    });
        } catch (JsonProcessingException | RuntimeException e) {
            // e.g. broker unreachable for longer than max.block.ms
            log.warn("Could not publish {} event {} to {}: {}", type, eventId, topic, e.getMessage());
        }
    }

    private void publishRest(String eventId, String type, Map<String, Object> event) {
        if (notificationServiceBaseUrl.isBlank()) {
            log.debug("Notification Service not configured; dropping {} event {}", type, eventId);
            return;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", internalApiToken);
        try {
            restTemplate.postForEntity(notificationServiceBaseUrl + "/internal/api/v1/events",
                    new HttpEntity<>(event, headers), String.class);
        } catch (RestClientException e) {
            log.warn("Could not publish {} event {}: {}", type, eventId, e.getMessage());
        }
    }
}
