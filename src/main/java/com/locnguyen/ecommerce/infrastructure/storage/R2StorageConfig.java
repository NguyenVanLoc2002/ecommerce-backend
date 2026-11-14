package com.locnguyen.ecommerce.infrastructure.storage;

import java.net.URI;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties(R2StorageProperties.class)
@ConditionalOnProperty(prefix = "app.storage.r2", name = "enabled", havingValue = "true")
public class R2StorageConfig {

    @Bean
    S3Client r2S3Client(R2StorageProperties properties) {
        return S3Client.builder()
                .endpointOverride(URI.create(required(properties.getEndpoint(), "endpoint")))
                .region(Region.of(required(properties.getRegion(), "region")))
                .credentialsProvider(credentials(properties))
                .forcePathStyle(true)
                .build();
    }

    @Bean
    S3Presigner r2S3Presigner(R2StorageProperties properties) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(required(properties.getEndpoint(), "endpoint")))
                .region(Region.of(required(properties.getRegion(), "region")))
                .credentialsProvider(credentials(properties))
                .build();
    }

    private StaticCredentialsProvider credentials(R2StorageProperties properties) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(
                required(properties.getAccessKeyId(), "access-key-id"),
                required(properties.getSecretAccessKey(), "secret-access-key")));
    }

    private String required(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("app.storage.r2." + property + " must be configured when R2 is enabled");
        }
        return value;
    }
}
