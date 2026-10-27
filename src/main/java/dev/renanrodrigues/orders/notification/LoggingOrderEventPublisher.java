package dev.renanrodrigues.orders.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.notifications", name = "broker", havingValue = "log")
public class LoggingOrderEventPublisher implements OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingOrderEventPublisher.class);

    @Override
    public void publish(OutboxEvent event) {
        log.info("Notificação do pedido [{}] na rota [{}]: {}", event.getAggregateId(), event.getRoutingKey(),
                event.getPayload());
    }
}
