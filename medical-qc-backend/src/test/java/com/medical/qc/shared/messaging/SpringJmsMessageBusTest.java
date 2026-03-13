package com.medical.qc.shared.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.jms.JmsException;
import org.springframework.jms.core.JmsTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SpringJmsMessageBusTest {

    @Test
    void sendShouldReturnTrueWhenJmsSendSucceeds() {
        JmsTemplate jmsTemplate = mock(JmsTemplate.class);
        SpringJmsMessageBus messageBus = new SpringJmsMessageBus(jmsTemplate);

        boolean sent = messageBus.send("qc.mock.quality.task", "payload");

        assertThat(sent).isTrue();
        verify(jmsTemplate).convertAndSend("qc.mock.quality.task", "payload");
    }

    @Test
    void sendShouldReturnFalseWhenJmsThrowsException() {
        JmsTemplate jmsTemplate = mock(JmsTemplate.class);
        doThrow(new JmsException("boom") {
        }).when(jmsTemplate).convertAndSend("qc.mock.quality.task", "payload");

        SpringJmsMessageBus messageBus = new SpringJmsMessageBus(jmsTemplate);

        boolean sent = messageBus.send("qc.mock.quality.task", "payload");

        assertThat(sent).isFalse();
    }
}
