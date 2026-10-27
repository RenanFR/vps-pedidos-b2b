package dev.renanrodrigues.orders.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class TestPublisherConfig {

    @Bean
    public RecordingOrderEventPublisher recordingOrderEventPublisher() {
        return new RecordingOrderEventPublisher();
    }
}
