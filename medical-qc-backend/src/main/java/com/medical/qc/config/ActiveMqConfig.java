package com.medical.qc.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

/**
 * ActiveMQ / JMS 配置。
 */
@Configuration(proxyBeanMethods = false)
@EnableJms
@ConditionalOnProperty(prefix = "app.messaging.activemq", name = "enabled", havingValue = "true")
public class ActiveMqConfig {
    @Bean
    public MessageConverter jacksonJmsMessageConverter(ObjectMapper objectMapper) {
        // 统一把消息序列化为 JSON 文本，避免不同消费者之间出现 Java 序列化兼容问题。
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setTargetType(MessageType.TEXT);
        // _type 字段用于在消息体中保存类型提示，便于 Spring JMS 反序列化。
        converter.setTypeIdPropertyName("_type");
        converter.setObjectMapper(objectMapper);
        return converter;
    }
}

