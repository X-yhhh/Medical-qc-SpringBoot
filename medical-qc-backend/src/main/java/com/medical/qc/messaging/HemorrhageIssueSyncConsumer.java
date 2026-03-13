package com.medical.qc.messaging;

import com.medical.qc.modules.issue.application.IssueServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

/**
 * 脑出血异常工单同步消费者。
 */
@Component
@ConditionalOnProperty(prefix = "app.messaging.activemq", name = "enabled", havingValue = "true")
public class HemorrhageIssueSyncConsumer {
    private static final Logger log = LoggerFactory.getLogger(HemorrhageIssueSyncConsumer.class);

    private final IssueServiceImpl issueService;

    public HemorrhageIssueSyncConsumer(IssueServiceImpl issueService) {
        this.issueService = issueService;
    }

    @JmsListener(destination = "${app.messaging.activemq.queue.hemorrhage-issue-sync}")
    public void consume(HemorrhageIssueSyncMessage message) {
        if (message == null || message.getTaskId() == null) {
            log.warn("收到无效的脑出血异常同步消息");
            return;
        }

        issueService.syncHemorrhageIssue(message.getTaskId());
    }
}
