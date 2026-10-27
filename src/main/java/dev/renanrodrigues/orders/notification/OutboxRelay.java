package dev.renanrodrigues.orders.notification;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(prefix = "app.outbox.relay", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository outboxEventRepository;
    private final OrderEventPublisher publisher;
    private final OutboxRelayProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public OutboxRelay(OutboxEventRepository outboxEventRepository, OrderEventPublisher publisher,
            OutboxRelayProperties properties, TransactionTemplate transactionTemplate, Clock clock,
            MeterRegistry meterRegistry) {
        this.outboxEventRepository = outboxEventRepository;
        this.publisher = publisher;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(fixedDelayString = "${app.outbox.relay.interval-ms}")
    public void relayOnSchedule() {
        relayPending();
    }

    public int relayPending() {
        Integer published = transactionTemplate.execute(status -> {
            List<OutboxEvent> events = outboxEventRepository.lockNextPending(properties.maxAttempts(),
                    properties.batchSize());
            return (int) events.stream().filter(this::publish).count();
        });
        return published == null ? 0 : published;
    }

    private boolean publish(OutboxEvent event) {
        try {
            publisher.publish(event);
            event.markPublished(clock.instant());
            meterRegistry.counter("outbox.events.published", "type", event.getEventType().name()).increment();
            return true;
        } catch (RuntimeException failure) {
            event.registerFailure(failure.getMessage());
            meterRegistry.counter("outbox.events.failed", "type", event.getEventType().name()).increment();
            if (event.getAttempts() >= properties.maxAttempts()) {
                log.error("Notificação [{}] do pedido [{}] abandonada depois de {} tentativas. Último erro: {}",
                        event.getId(), event.getAggregateId(), event.getAttempts(), event.getLastError());
            } else {
                log.warn("Falha ao publicar a notificação [{}] do pedido [{}], tentativa {}. Motivo: {}",
                        event.getId(), event.getAggregateId(), event.getAttempts(), event.getLastError());
            }
            return false;
        }
    }
}
