package dev.renanrodrigues.orders.notification;

public interface OrderEventPublisher {

    void publish(OutboxEvent event);
}
