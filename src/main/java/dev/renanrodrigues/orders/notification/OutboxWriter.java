package dev.renanrodrigues.orders.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.renanrodrigues.orders.config.CorrelationIdFilter;
import dev.renanrodrigues.orders.order.OrderStatus;
import dev.renanrodrigues.orders.order.PurchaseOrder;
import jakarta.persistence.EntityManager;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(propagation = Propagation.MANDATORY)
public class OutboxWriter {

    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    public OutboxWriter(EntityManager entityManager, ObjectMapper objectMapper) {
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
    }

    public void orderCreated(PurchaseOrder order) {
        append(new OrderEvent(UUID.randomUUID(), OutboxEventType.ORDER_CREATED, order.getId(), order.getPartnerId(),
                null, order.getStatus(), order.getTotalAmount(), null, MDC.get(CorrelationIdFilter.MDC_KEY),
                order.getCreatedAt()), "order.created");
    }

    public void orderStatusChanged(PurchaseOrder order, OrderStatus previousStatus, String reason) {
        append(new OrderEvent(UUID.randomUUID(), OutboxEventType.ORDER_STATUS_CHANGED, order.getId(),
                order.getPartnerId(), previousStatus, order.getStatus(), order.getTotalAmount(), reason,
                MDC.get(CorrelationIdFilter.MDC_KEY), order.getUpdatedAt()),
                "order.status." + order.getStatus().name().toLowerCase(Locale.ROOT));
    }

    private void append(OrderEvent event, String routingKey) {
        try {
            entityManager.persist(OutboxEvent.pending(event.eventId(), event.orderId(), event.eventType(), routingKey,
                    objectMapper.writeValueAsString(event), event.occurredAt()));
        } catch (JsonProcessingException serializationFailure) {
            throw new IllegalStateException("Não foi possível serializar o evento do pedido " + event.orderId(),
                    serializationFailure);
        }
    }
}
