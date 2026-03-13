package com.medical.qc.modules.issue.application.command;

/**
 * 异常工单状态更新命令。
 */
public record IssueStatusUpdateCommand(Long scopedUserId,
                                       Long operatorId,
                                       Long issueId,
                                       String status,
                                       String remark) {
}

