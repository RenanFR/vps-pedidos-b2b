package dev.renanrodrigues.orders.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    private static final int MAX_ERROR_LENGTH = 500;

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID aggregateId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private OutboxEventType eventType;

    @Column(nullable = false, length = 100)
    private String routingKey;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant publishedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(length = MAX_ERROR_LENGTH)
    private String lastError;

    protected OutboxEvent() {
    }

    public static OutboxEvent pending(UUID id, UUID aggregateId, OutboxEventType eventType, String routingKey,
            String payload, Instant createdAt) {
        OutboxEvent event = new OutboxEvent();
        event.id = id;
        event.aggregateId = aggregateId;
        event.eventType = eventType;
        event.routingKey = routingKey;
        event.payload = payload;
        event.createdAt = createdAt;
        return event;
    }

    public void markPublished(Instant now) {
        attempts++;
        publishedAt = now;
        lastError = null;
    }

    public void registerFailure(String error) {
        attempts++;
        lastError = Optional.ofNullable(error)
                .map(message -> message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message)
                .orElse("Falha sem mensagem");
    }

    public UUID getId() {
        return id;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public OutboxEventType getEventType() {
        return eventType;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }
}
