package com.medical.qc.service;

import com.medical.qc.entity.HemorrhageRecord;

import java.util.List;
import java.util.Map;

/**
 * 异常工单服务。
 *
 * <p>负责将脑出血历史记录同步为异常工单，并为首页/异常汇总页提供统一的数据查询与状态流转能力。</p>
 */
public interface IssueService {
    /**
     * 同步单条脑出血记录对应的异常工单。
     *
     * @param record 脑出血历史记录
     */
    void syncHemorrhageIssue(HemorrhageRecord record);

    /**
     * 回填指定用户的脑出血历史记录到异常工单表。
     *
     * @param userId 用户 ID
     */
    void syncHemorrhageIssues(Long userId);

    /**
     * 统计当前未解决的异常任务数。
     *
     * @param userId 用户 ID
     * @return 未解决任务数
     */
    long countPendingIssues(Long userId);

    /**
     * 统计当前高优先级且未解决的风险工单数。
     *
     * @param userId 用户 ID
     * @return 高优先级风险数
     */
    long countHighRiskIssues(Long userId);

    /**
     * 获取首页近期风险预警列表。
     *
     * @param userId 用户 ID
     * @param limit  返回数量上限
     * @return 风险预警列表
     */
    List<Map<String, Object>> getRiskAlerts(Long userId, int limit);

    /**
     * 获取异常汇总页顶部统计卡片数据。
     *
     * @param userId 用户 ID
     * @return 统计结果
     */
    Map<String, Object> getSummaryStats(Long userId);

    /**
     * 获取异常趋势图数据。
     *
     * @param userId 用户 ID
     * @param days   统计天数
     * @return 趋势图数据
     */
    Map<String, Object> getIssueTrend(Long userId, int days);

    /**
     * 获取异常类型分布数据。
     *
     * @param userId 用户 ID
     * @return 分布列表
     */
    List<Map<String, Object>> getIssueDistribution(Long userId);

    /**
     * 获取异常工单分页列表。
     *
     * @param userId 用户 ID
     * @param page   页码
     * @param limit  每页数量
     * @param query  搜索关键字
     * @param status 状态筛选
     * @return 分页结果
     */
    Map<String, Object> getIssuePage(Long userId, int page, int limit, String query, String status);

    /**
     * 获取单条异常工单详情。
     *
     * @param userId  用户 ID
     * @param issueId 工单 ID
     * @return 工单详情
     */
    Map<String, Object> getIssueDetail(Long userId, Long issueId);

    /**
     * 更新异常工单状态。
     *
     * @param userId     当前用户 ID
     * @param operatorId 操作人 ID
     * @param issueId    工单 ID
     * @param status     新状态
     * @param remark     处理备注
     * @return 更新后的工单摘要
     */
    Map<String, Object> updateIssueStatus(Long userId, Long operatorId, Long issueId, String status, String remark);
}
