package com.ecom.payments.configs;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Creates this service's event topic on startup if it does not exist (no-op when it does). */
@Configuration
public class KafkaTopicConfig {
    @Bean
    public NewTopic eventsTopic(@Value("${events.topic}") String topic,
                                @Value("${events.topic-partitions:3}") int partitions,
                                @Value("${events.topic-replicas:1}") int replicas) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(replicas).build();
    }
}
