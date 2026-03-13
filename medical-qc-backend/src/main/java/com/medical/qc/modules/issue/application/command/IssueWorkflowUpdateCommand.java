package com.medical.qc.modules.issue.application.command;

import com.medical.qc.bean.IssueWorkflowUpdateReq;

/**
 * 异常工单工作流更新命令。
 */
public record IssueWorkflowUpdateCommand(Long scopedUserId,
                                         Long operatorId,
                                         Long issueId,
                                         IssueWorkflowUpdateReq requestBody) {
}

