package com.example.bankcore.notificationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The notification service, extracted from the monolith in Phase 13.
 *
 * <p>It owns its notifications and its database, and it learns about the rest of the bank only
 * through Kafka events. It never calls the monolith and the monolith never calls it, which is
 * what makes the two independently deployable — and what makes the notification path unable to
 * slow down or fail a transfer.
 *
 * <p>Read {@code docs/architecture/adr/ADR-002-service-extraction.md} for why this module and
 * only this module was extracted.
 */
@SpringBootApplication
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
