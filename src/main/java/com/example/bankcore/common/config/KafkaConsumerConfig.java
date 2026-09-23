package com.example.bankcore.common.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Consumer retry and dead-letter handling.
 *
 * <p>Failures come in two kinds and need opposite treatment:
 *
 * <ul>
 *   <li><b>Transient</b> — the database was briefly unavailable. Retrying works, so the container
 *       backs off exponentially instead of hammering a struggling dependency.</li>
 *   <li><b>Permanent</b> — the message is malformed, or the work will never succeed. Retrying
 *       forever blocks the partition behind it and turns one bad message into an outage.</li>
 * </ul>
 *
 * <p>After the attempts are exhausted the record is republished to {@code <topic>.DLT}, where it
 * can be inspected and replayed. That is the difference between a failure that is observable and
 * one that is a silent gap in the data.
 */
@Configuration
@ConditionalOnProperty(name = "bankcore.events.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, exception) -> {
                    log.error("Sending record to the dead-letter topic: topic={} partition={} offset={}",
                            record.topic(), record.partition(), record.offset(), exception);
                    return new org.apache.kafka.common.TopicPartition(record.topic() + ".DLT", -1);
                });

        ExponentialBackOff backOff = new ExponentialBackOff(500, 2.0);
        backOff.setMaxElapsedTime(30_000);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);

        // A message that can never be parsed will not become parseable on the fourth attempt;
        // send it straight to the dead-letter topic instead of blocking its partition.
        handler.addNotRetryableExceptions(
                com.fasterxml.jackson.core.JsonProcessingException.class,
                IllegalArgumentException.class);

        handler.setRetryListeners((ConsumerRecord<?, ?> record, Exception exception, int deliveryAttempt) ->
                log.warn("Retrying a failed record: topic={} offset={} attempt={}",
                        record.topic(), record.offset(), deliveryAttempt));

        return handler;
    }
}
