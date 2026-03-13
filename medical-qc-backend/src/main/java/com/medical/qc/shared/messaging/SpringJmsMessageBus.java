package com.medical.qc.shared.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.jms.JmsException;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 基于 Spring JMS 的消息总线实现。
 */
@Component
@Primary
@ConditionalOnBean(JmsTemplate.class)
public class SpringJmsMessageBus implements MessageBus {
    private static final Logger logger = LoggerFactory.getLogger(SpringJmsMessageBus.class);

    private final JmsTemplate jmsTemplate;

    public SpringJmsMessageBus(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    @Override
    public boolean send(String destination, Object payload) {
        if (!StringUtils.hasText(destination)) {
            return false;
        }

        try {
            jmsTemplate.convertAndSend(destination, payload);
            return true;
        } catch (JmsException exception) {
            logger.warn("消息发送失败，destination={}", destination, exception);
            return false;
        }
    }
}

