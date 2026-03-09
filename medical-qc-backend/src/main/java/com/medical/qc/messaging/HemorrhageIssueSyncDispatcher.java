package com.medical.qc.messaging;

import com.medical.qc.config.ActiveMqProperties;
import com.medical.qc.entity.HemorrhageRecord;
import com.medical.qc.service.IssueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jms.JmsException;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 脑出血异常工单同步消息分发器。
 */
@Component
public class HemorrhageIssueSyncDispatcher {
    private static final Logger log = LoggerFactory.getLogger(HemorrhageIssueSyncDispatcher.class);

    private final ActiveMqProperties activeMqProperties;
    private final ObjectProvider<JmsTemplate> jmsTemplateProvider;
    private final IssueService issueService;

    public HemorrhageIssueSyncDispatcher(ActiveMqProperties activeMqProperties,
                                         ObjectProvider<JmsTemplate> jmsTemplateProvider,
                                         IssueService issueService) {
        this.activeMqProperties = activeMqProperties;
        this.jmsTemplateProvider = jmsTemplateProvider;
        this.issueService = issueService;
    }

    public void dispatch(HemorrhageRecord record) {
        if (record == null || record.getId() == null) {
            return;
        }

        if (!activeMqProperties.isEnabled()) {
            issueService.syncHemorrhageIssue(record);
            return;
        }

        JmsTemplate jmsTemplate = jmsTemplateProvider.getIfAvailable();
        if (jmsTemplate == null) {
            log.warn("ActiveMQ 已启用，但当前未找到 JmsTemplate，改为同步生成异常工单，recordId={}", record.getId());
            issueService.syncHemorrhageIssue(record);
            return;
        }

        try {
            jmsTemplate.convertAndSend(
                    activeMqProperties.getQueue().getHemorrhageIssueSync(),
                    new HemorrhageIssueSyncMessage(record.getId(), record.getUserId(), LocalDateTime.now()));
        } catch (JmsException ex) {
            log.warn("发送 ActiveMQ 消息失败，改为同步生成异常工单，recordId={}", record.getId(), ex);
            issueService.syncHemorrhageIssue(record);
        }
    }
}
