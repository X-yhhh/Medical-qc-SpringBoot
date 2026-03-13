package com.medical.qc.messaging;

import com.medical.qc.config.ActiveMqProperties;
import com.medical.qc.modules.issue.application.IssueServiceImpl;
import com.medical.qc.shared.messaging.MessageBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 脑出血异常工单同步消息分发器。
 */
@Component
public class HemorrhageIssueSyncDispatcher {
    private static final Logger log = LoggerFactory.getLogger(HemorrhageIssueSyncDispatcher.class);

    private final ActiveMqProperties activeMqProperties;
    private final IssueServiceImpl issueService;
    private final MessageBus messageBus;

    public HemorrhageIssueSyncDispatcher(ActiveMqProperties activeMqProperties,
                                         IssueServiceImpl issueService,
                                         MessageBus messageBus) {
        this.activeMqProperties = activeMqProperties;
        this.issueService = issueService;
        this.messageBus = messageBus;
    }

    public void dispatch(Long taskId, Long userId) {
        if (taskId == null) {
            return;
        }

        if (!activeMqProperties.isEnabled()) {
            issueService.syncHemorrhageIssue(taskId);
            return;
        }

        boolean sent = messageBus.send(
                activeMqProperties.getQueue().getHemorrhageIssueSync(),
                new HemorrhageIssueSyncMessage(taskId, userId, LocalDateTime.now()));
        if (!sent) {
            log.warn("消息总线未接管脑出血异常同步，改为同步生成异常工单，taskId={}", taskId);
            issueService.syncHemorrhageIssue(taskId);
        }
    }
}
