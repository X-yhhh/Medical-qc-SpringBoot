package com.medical.qc.messaging;

import com.medical.qc.entity.HemorrhageRecord;
import com.medical.qc.mapper.HemorrhageRecordMapper;
import com.medical.qc.service.IssueService;
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

    private final HemorrhageRecordMapper hemorrhageRecordMapper;
    private final IssueService issueService;

    public HemorrhageIssueSyncConsumer(HemorrhageRecordMapper hemorrhageRecordMapper,
                                       IssueService issueService) {
        this.hemorrhageRecordMapper = hemorrhageRecordMapper;
        this.issueService = issueService;
    }

    @JmsListener(destination = "${app.messaging.activemq.queue.hemorrhage-issue-sync}")
    public void consume(HemorrhageIssueSyncMessage message) {
        if (message == null || message.getRecordId() == null) {
            log.warn("收到无效的脑出血异常同步消息");
            return;
        }

        HemorrhageRecord record = hemorrhageRecordMapper.selectById(message.getRecordId());
        if (record == null) {
            log.warn("脑出血记录不存在，忽略异常同步消息，recordId={}", message.getRecordId());
            return;
        }

        issueService.syncHemorrhageIssue(record);
    }
}
