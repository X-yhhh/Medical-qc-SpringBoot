package com.medical.qc.modules.issue.application.query;

/**
 * 异常工单分页查询条件。
 */
public record IssuePageQuery(Long scopedUserId,
                             int page,
                             int limit,
                             String query,
                             String status) {
}

