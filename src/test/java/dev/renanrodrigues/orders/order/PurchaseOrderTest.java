package dev.renanrodrigues.orders.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.renanrodrigues.orders.error.InvalidStatusTransitionException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PurchaseOrderTest {

    private final Instant now = Instant.parse("2026-10-27T12:00:00Z");

    @Test
    void totalIsTheSumOfEveryItemSubtotal() {
        PurchaseOrder order = PurchaseOrder.create(UUID.randomUUID(), List.of(
                new OrderItem("SKU-A", 3, new BigDecimal("10.10")),
                new OrderItem("SKU-B", 2, new BigDecimal("0.05"))), null, "hash", now);

        assertThat(order.getTotalAmount()).isEqualByComparingTo("30.40");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDENTE);
        assertThat(order.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void refusesTransitionOutsideTheFlow() {
        PurchaseOrder order = PurchaseOrder.create(UUID.randomUUID(),
                List.of(new OrderItem("SKU-A", 1, BigDecimal.TEN)), null, "hash", now);

        assertThatThrownBy(() -> order.changeStatus(OrderStatus.ENVIADO, now))
                .isInstanceOf(InvalidStatusTransitionException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDENTE);
    }
}
