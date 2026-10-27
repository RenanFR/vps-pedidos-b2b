package dev.renanrodrigues.orders.support;

import dev.renanrodrigues.orders.notification.OrderEventPublisher;
import dev.renanrodrigues.orders.notification.OutboxEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class RecordingOrderEventPublisher implements OrderEventPublisher {

    private final List<OutboxEvent> published = new CopyOnWriteArrayList<>();
    private final AtomicBoolean brokerDown = new AtomicBoolean(false);

    @Override
    public void publish(OutboxEvent event) {
        if (brokerDown.get()) {
            throw new IllegalStateException("Broker indisponível");
        }
        published.add(event);
    }

    public List<OutboxEvent> published() {
        return List.copyOf(published);
    }

    public void simulateBrokerDown(boolean down) {
        brokerDown.set(down);
    }

    public void reset() {
        published.clear();
        brokerDown.set(false);
    }
}
