package dev.renanrodrigues.orders.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.outbox.relay")
public record OutboxRelayProperties(boolean enabled, long intervalMs, int batchSize, int maxAttempts) {
}
