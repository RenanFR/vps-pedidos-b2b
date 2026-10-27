package dev.renanrodrigues.orders.notification;

import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.notifications.demo-consumer", name = "enabled", havingValue = "true")
public class DemoNotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(DemoNotificationConsumer.class);

    @RabbitListener(queues = "${app.notifications.queue}")
    public void onNotification(Message message) {
        log.info("Consumidor de demonstração recebeu o evento [{}] pela rota [{}]: {}",
                message.getMessageProperties().getMessageId(),
                message.getMessageProperties().getReceivedRoutingKey(),
                new String(message.getBody(), StandardCharsets.UTF_8));
    }
}
