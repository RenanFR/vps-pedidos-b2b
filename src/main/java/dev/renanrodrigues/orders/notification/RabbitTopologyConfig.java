package dev.renanrodrigues.orders.notification;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "app.notifications", name = "broker", havingValue = "rabbitmq", matchIfMissing = true)
public class RabbitTopologyConfig {

    @Bean
    public TopicExchange ordersExchange(NotificationProperties properties) {
        return new TopicExchange(properties.exchange(), true, false);
    }

    @Bean
    public Queue notificationsQueue(NotificationProperties properties) {
        return QueueBuilder.durable(properties.queue()).build();
    }

    @Bean
    public Binding notificationsBinding(Queue notificationsQueue, TopicExchange ordersExchange) {
        return BindingBuilder.bind(notificationsQueue).to(ordersExchange).with("order.#");
    }
}
