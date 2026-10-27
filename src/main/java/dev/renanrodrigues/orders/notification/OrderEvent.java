package dev.renanrodrigues.orders.notification;

import dev.renanrodrigues.orders.order.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderEvent(
        UUID eventId,
        OutboxEventType eventType,
        UUID orderId,
        UUID partnerId,
        OrderStatus previousStatus,
        OrderStatus currentStatus,
        BigDecimal totalAmount,
        String reason,
        String correlationId,
        Instant occurredAt) {
}
