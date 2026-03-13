package com.medical.qc.shared.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.jms.JmsException;
import org.springframework.jms.core.JmsTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * SpringJmsMessageBus 单元测试。
 * 校验 JMS 成功发送和异常降级两个分支。
 */
class SpringJmsMessageBusTest {

    @Test
    void sendShouldReturnTrueWhenJmsSendSucceeds() {
        // 使用 Mock 的 JmsTemplate 验证 convertAndSend 是否被正确调用。
        JmsTemplate jmsTemplate = mock(JmsTemplate.class);
        SpringJmsMessageBus messageBus = new SpringJmsMessageBus(jmsTemplate);

        boolean sent = messageBus.send("qc.mock.quality.task", "payload");

        // 成功发送时应该返回 true，且消息主题和负载保持原样。
        assertThat(sent).isTrue();
        verify(jmsTemplate).convertAndSend("qc.mock.quality.task", "payload");
    }

    @Test
    void sendShouldReturnFalseWhenJmsThrowsException() {
        JmsTemplate jmsTemplate = mock(JmsTemplate.class);
        // 模拟底层 JMS 发送失败，验证消息总线是否转换为 false 而不是直接抛错。
        doThrow(new JmsException("boom") {
        }).when(jmsTemplate).convertAndSend("qc.mock.quality.task", "payload");

        SpringJmsMessageBus messageBus = new SpringJmsMessageBus(jmsTemplate);

        boolean sent = messageBus.send("qc.mock.quality.task", "payload");

        // 失败分支返回 false，便于上层业务决定是否重试。
        assertThat(sent).isFalse();
    }
}
