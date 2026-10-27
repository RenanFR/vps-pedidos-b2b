package dev.renanrodrigues.orders.error;

import dev.renanrodrigues.orders.order.OrderStatus;
import java.util.UUID;

public class InvalidStatusTransitionException extends RuntimeException {

    private final OrderStatus currentStatus;
    private final OrderStatus requestedStatus;

    public InvalidStatusTransitionException(UUID orderId, OrderStatus currentStatus, OrderStatus requestedStatus) {
        super("O pedido " + orderId + " está " + currentStatus + " e não pode passar para " + requestedStatus);
        this.currentStatus = currentStatus;
        this.requestedStatus = requestedStatus;
    }

    public OrderStatus getCurrentStatus() {
        return currentStatus;
    }

    public OrderStatus getRequestedStatus() {
        return requestedStatus;
    }
}
