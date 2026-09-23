package com.example.bankcore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
// Reference data is read constantly and changed rarely. The cache is in-memory for now;
// Phase 09 moves it to Redis so several instances share one.
@EnableCaching
// The outbox publisher runs on a schedule, so it also drains events a crash left behind.
@EnableScheduling
public class BankCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankCoreApplication.class, args);
    }
}
