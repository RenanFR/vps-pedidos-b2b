package dev.renanrodrigues.orders.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.renanrodrigues.orders.notification.OutboxEvent;
import dev.renanrodrigues.orders.notification.OutboxEventRepository;
import dev.renanrodrigues.orders.notification.OutboxEventType;
import dev.renanrodrigues.orders.notification.OutboxRelay;
import dev.renanrodrigues.orders.partner.Partner;
import dev.renanrodrigues.orders.partner.PartnerRepository;
import dev.renanrodrigues.orders.support.IntegrationTest;
import dev.renanrodrigues.orders.support.RecordingOrderEventPublisher;
import dev.renanrodrigues.orders.support.TestData;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class OutboxRelayIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private PartnerRepository partnerRepository;

    @Autowired
    private RecordingOrderEventPublisher publisher;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void drainPreviousEvents() {
        outboxRelay.relayPending();
        publisher.reset();
    }

    @Test
    void publishesCreationAndEveryStatusChangeInOrder() throws Exception {
        Partner partner = TestData.partnerWithCredit(partnerRepository, "1000.00");
        UUID orderId = orderService.create(TestData.orderOf(partner.getId(), 2, "50.00"), null).order().id();
        orderService.changeStatus(orderId, OrderStatus.APROVADO, "Pagamento confirmado");

        assertThat(outboxRelay.relayPending()).isEqualTo(2);

        List<OutboxEvent> published = publisher.published();
        assertThat(published).extracting(OutboxEvent::getRoutingKey)
                .containsExactly("order.created", "order.status.aprovado");
        JsonNode statusChange = objectMapper.readTree(published.get(1).getPayload());
        assertThat(statusChange.get("eventType").asText()).isEqualTo(OutboxEventType.ORDER_STATUS_CHANGED.name());
        assertThat(statusChange.get("previousStatus").asText()).isEqualTo("PENDENTE");
        assertThat(statusChange.get("currentStatus").asText()).isEqualTo("APROVADO");
        assertThat(statusChange.get("reason").asText()).isEqualTo("Pagamento confirmado");
        assertThat(outboxRelay.relayPending()).isZero();
    }

    @Test
    void rejectedStatusChangeLeavesNoEvent() {
        Partner partner = TestData.partnerWithCredit(partnerRepository, "1000.00");
        UUID orderId = orderService.create(TestData.orderOf(partner.getId(), 1, "10.00"), null).order().id();

        try {
            orderService.changeStatus(orderId, OrderStatus.ENTREGUE, null);
        } catch (RuntimeException expected) {
            assertThat(expected).hasMessageContaining("não pode passar");
        }

        assertThat(outboxEventRepository.findByAggregateIdOrderByCreatedAt(orderId))
                .extracting(OutboxEvent::getEventType)
                .containsExactly(OutboxEventType.ORDER_CREATED);
    }

    @Test
    void keepsEventPendingWhileBrokerIsDownAndDeliversAfterRecovery() {
        Partner partner = TestData.partnerWithCredit(partnerRepository, "1000.00");
        UUID orderId = orderService.create(TestData.orderOf(partner.getId(), 1, "10.00"), null).order().id();

        publisher.simulateBrokerDown(true);
        assertThat(outboxRelay.relayPending()).isZero();
        OutboxEvent failed = outboxEventRepository.findByAggregateIdOrderByCreatedAt(orderId).get(0);
        assertThat(failed.getPublishedAt()).isNull();
        assertThat(failed.getAttempts()).isEqualTo(1);
        assertThat(failed.getLastError()).isEqualTo("Broker indisponível");

        publisher.simulateBrokerDown(false);
        assertThat(outboxRelay.relayPending()).isEqualTo(1);
        assertThat(outboxEventRepository.findByAggregateIdOrderByCreatedAt(orderId).get(0).getPublishedAt())
                .isNotNull();
    }
}
