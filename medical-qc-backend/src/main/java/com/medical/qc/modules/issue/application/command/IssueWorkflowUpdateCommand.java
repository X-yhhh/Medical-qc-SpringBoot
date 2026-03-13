package com.medical.qc.modules.issue.application.command;

import com.medical.qc.bean.IssueWorkflowUpdateReq;

/**
 * 异常工单工作流更新命令。
 *
 * @param scopedUserId 当前用户可访问的数据范围
 * @param operatorId 当前操作人 ID
 * @param issueId 目标工单 ID
 * @param requestBody 包含状态、指派人和 CAPA 信息的请求体
 */
public record IssueWorkflowUpdateCommand(Long scopedUserId,
                                         Long operatorId,
                                         Long issueId,
                                         IssueWorkflowUpdateReq requestBody) {
}

