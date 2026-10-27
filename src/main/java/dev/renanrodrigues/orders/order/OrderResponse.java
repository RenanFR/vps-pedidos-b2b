package dev.renanrodrigues.orders.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID partnerId,
        List<OrderItemResponse> items,
        BigDecimal totalAmount,
        OrderStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static OrderResponse from(PurchaseOrder order) {
        return new OrderResponse(order.getId(), order.getPartnerId(),
                order.getItems().stream().map(OrderItemResponse::from).toList(),
                order.getTotalAmount(), order.getStatus(), order.getCreatedAt(), order.getUpdatedAt());
    }
}
