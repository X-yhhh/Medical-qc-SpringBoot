package com.medical.qc.modules.issue.application.query;

/**
 * 异常工单分页查询条件。
 *
 * @param scopedUserId 数据范围用户 ID；管理员传 null 表示全量
 * @param page 当前页码
 * @param limit 每页大小
 * @param query 患者姓名/检查号模糊搜索词
 * @param status 工单状态筛选
 */
public record IssuePageQuery(Long scopedUserId,
                             int page,
                             int limit,
                             String query,
                             String status) {
}

