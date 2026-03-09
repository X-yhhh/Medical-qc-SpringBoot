package com.medical.qc.messaging;

import com.medical.qc.service.MockQualityTaskService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

/**
 * mock 质控异步任务消费者。
 */
@Component
@ConditionalOnProperty(prefix = "app.messaging.activemq", name = "enabled", havingValue = "true")
public class MockQualityTaskConsumer {
    private final MockQualityTaskService mockQualityTaskService;

    public MockQualityTaskConsumer(MockQualityTaskService mockQualityTaskService) {
        this.mockQualityTaskService = mockQualityTaskService;
    }

    @JmsListener(destination = "${app.messaging.activemq.queue.mock-quality-task}")
    public void consume(MockQualityTaskMessage message) {
        mockQualityTaskService.processTask(message);
    }
}
