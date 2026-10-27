package dev.renanrodrigues.orders.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OrderStatusTest {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
            OrderStatus.PENDENTE, EnumSet.of(OrderStatus.APROVADO, OrderStatus.CANCELADO),
            OrderStatus.APROVADO, EnumSet.of(OrderStatus.EM_PROCESSAMENTO, OrderStatus.CANCELADO),
            OrderStatus.EM_PROCESSAMENTO, EnumSet.of(OrderStatus.ENVIADO, OrderStatus.CANCELADO),
            OrderStatus.ENVIADO, EnumSet.of(OrderStatus.ENTREGUE),
            OrderStatus.ENTREGUE, EnumSet.noneOf(OrderStatus.class),
            OrderStatus.CANCELADO, EnumSet.noneOf(OrderStatus.class));

    @Test
    void onlyTheDocumentedTransitionsAreAllowed() {
        for (OrderStatus current : OrderStatus.values()) {
            for (OrderStatus target : OrderStatus.values()) {
                assertThat(current.canTransitionTo(target))
                        .as("transição de %s para %s", current, target)
                        .isEqualTo(ALLOWED.get(current).contains(target));
            }
        }
    }

    @Test
    void creditIsHeldFromApprovalOnward() {
        assertThat(EnumSet.allOf(OrderStatus.class)).filteredOn(OrderStatus::holdsPartnerCredit)
                .containsExactlyInAnyOrder(OrderStatus.APROVADO, OrderStatus.EM_PROCESSAMENTO, OrderStatus.ENVIADO,
                        OrderStatus.ENTREGUE);
    }
}
