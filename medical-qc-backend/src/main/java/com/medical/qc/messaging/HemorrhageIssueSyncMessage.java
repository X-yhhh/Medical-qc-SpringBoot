package com.medical.qc.messaging;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 脑出血异常工单同步消息。
 */
public class HemorrhageIssueSyncMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long recordId;
    private Long userId;
    private LocalDateTime publishedAt;

    public HemorrhageIssueSyncMessage() {
    }

    public HemorrhageIssueSyncMessage(Long recordId, Long userId, LocalDateTime publishedAt) {
        this.recordId = recordId;
        this.userId = userId;
        this.publishedAt = publishedAt;
    }

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
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
