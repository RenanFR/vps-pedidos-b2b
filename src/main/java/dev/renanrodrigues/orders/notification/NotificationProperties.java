package dev.renanrodrigues.orders.notification;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.notifications")
public record NotificationProperties(String broker, String exchange, String queue, Duration confirmTimeout) {
}
