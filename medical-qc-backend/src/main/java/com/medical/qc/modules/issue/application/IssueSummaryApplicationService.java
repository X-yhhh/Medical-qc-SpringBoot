package com.medical.qc.modules.issue.application;

import com.medical.qc.modules.issue.application.command.IssueStatusUpdateCommand;
import com.medical.qc.modules.issue.application.command.IssueWorkflowUpdateCommand;
import com.medical.qc.modules.issue.application.query.IssuePageQuery;
import com.medical.qc.modules.unified.application.UnifiedIssueQueryService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 异常汇总与工单应用服务。
 *
 * <p>查询统一走新模型查询服务，工作流更新走新模型写服务。</p>
 */
@Service
public class IssueSummaryApplicationService {
    // 写操作由 issueService 负责，保持应用服务层只做入口编排。
    private final IssueServiceImpl issueService;
    // 聚合查询全部走统一模型查询服务，保证页面统计口径一致。
    private final UnifiedIssueQueryService unifiedIssueQueryService;

    public IssueSummaryApplicationService(IssueServiceImpl issueService,
                                          UnifiedIssueQueryService unifiedIssueQueryService) {
        this.issueService = issueService;
        this.unifiedIssueQueryService = unifiedIssueQueryService;
    }

    /**
     * 获取顶部统计卡片数据。
     */
    public Map<String, Object> getSummaryStats(Long scopedUserId) {
        return unifiedIssueQueryService.getSummaryStats(scopedUserId);
    }

    /**
     * 获取趋势图数据。
     */
    public Map<String, Object> getIssueTrend(Long scopedUserId, int days) {
        return unifiedIssueQueryService.getIssueTrend(scopedUserId, days);
    }

    /**
     * 获取异常类型分布。
     */
    public List<Map<String, Object>> getIssueDistribution(Long scopedUserId) {
        return unifiedIssueQueryService.getIssueDistribution(scopedUserId);
    }

    /**
     * 查询可分派人员列表。
     */
    public List<Map<String, Object>> getAssignableUsers() {
        return issueService.getAssignableUsers();
    }

    /**
     * 获取异常工单分页数据。
     */
    public Map<String, Object> getIssuePage(IssuePageQuery query) {
        return unifiedIssueQueryService.getIssuePage(
                query.scopedUserId(),
                query.page(),
                query.limit(),
                query.query(),
                query.status());
    }

    /**
     * 获取单条工单详情。
     */
    public Map<String, Object> getIssueDetail(Long scopedUserId, Long issueId) {
        return unifiedIssueQueryService.getIssueDetail(scopedUserId, issueId);
    }

    /**
     * 更新工单状态。
     */
    public Map<String, Object> updateIssueStatus(IssueStatusUpdateCommand command) {
        return issueService.updateIssueStatus(
                command.scopedUserId(),
                command.operatorId(),
                command.issueId(),
                command.status(),
                command.remark());
    }

    /**
     * 更新工单流转字段和 CAPA 信息。
     */
    public Map<String, Object> updateIssueWorkflow(IssueWorkflowUpdateCommand command) {
        return issueService.updateIssueWorkflow(
                command.scopedUserId(),
                command.operatorId(),
                command.issueId(),
                command.requestBody());
    }
}
