package com.medical.qc.modules.issue.application.command;

/**
 * 异常工单状态更新命令。
 *
 * @param scopedUserId 当前用户的数据范围；管理员为 null，医生为本人 ID
 * @param operatorId 当前执行更新操作的用户 ID
 * @param issueId 目标工单 ID
 * @param status 目标状态
 * @param remark 本次状态流转备注
 */
public record IssueStatusUpdateCommand(Long scopedUserId,
                                       Long operatorId,
                                       Long issueId,
                                       String status,
                                       String remark) {
}

