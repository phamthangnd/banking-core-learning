package com.example.bankcore.file.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Object-storage settings, bound from {@code bankcore.file.*}.
 *
 * @param endpoint   S3-compatible endpoint; MinIO locally, the provider's URL in production
 * @param bucket     bucket that holds every object
 * @param region     required by the S3 protocol even when the provider ignores it
 * @param accessKey  credentials; supplied by the environment, never committed
 * @param secretKey  credentials; supplied by the environment, never committed
 */
@ConfigurationProperties(prefix = "bankcore.file")
@Validated
public record FileProperties(
        @DefaultValue("http://localhost:9000") @NotBlank String endpoint,
        @DefaultValue("bankcore") @NotBlank String bucket,
        @DefaultValue("us-east-1") @NotBlank String region,
        @DefaultValue("") String accessKey,
        @DefaultValue("") String secretKey
) {
}
