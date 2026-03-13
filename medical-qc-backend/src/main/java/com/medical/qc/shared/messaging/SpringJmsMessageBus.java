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
    // 统一记录 ActiveMQ 发送失败的情况，供降级逻辑和排查使用。
    private static final Logger logger = LoggerFactory.getLogger(SpringJmsMessageBus.class);

    // 底层发送能力由 Spring JMS 模板提供。
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
            // 发送成功后返回 true，调用方据此决定是否需要本地降级执行。
            jmsTemplate.convertAndSend(destination, payload);
            return true;
        } catch (JmsException exception) {
            logger.warn("消息发送失败，destination={}", destination, exception);
            return false;
        }
    }
}

