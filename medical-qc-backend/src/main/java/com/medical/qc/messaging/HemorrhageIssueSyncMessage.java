package com.medical.qc.messaging;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 脑出血异常工单同步消息。
 */
public class HemorrhageIssueSyncMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long taskId;
    private Long userId;
    private LocalDateTime publishedAt;

    public HemorrhageIssueSyncMessage() {
    }

    public HemorrhageIssueSyncMessage(Long taskId, Long userId, LocalDateTime publishedAt) {
        this.taskId = taskId;
        this.userId = userId;
        this.publishedAt = publishedAt;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }
}
