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
    private final IssueServiceImpl issueService;
    private final UnifiedIssueQueryService unifiedIssueQueryService;

    public IssueSummaryApplicationService(IssueServiceImpl issueService,
                                          UnifiedIssueQueryService unifiedIssueQueryService) {
        this.issueService = issueService;
        this.unifiedIssueQueryService = unifiedIssueQueryService;
    }

    public Map<String, Object> getSummaryStats(Long scopedUserId) {
        return unifiedIssueQueryService.getSummaryStats(scopedUserId);
    }

    public Map<String, Object> getIssueTrend(Long scopedUserId, int days) {
        return unifiedIssueQueryService.getIssueTrend(scopedUserId, days);
    }

    public List<Map<String, Object>> getIssueDistribution(Long scopedUserId) {
        return unifiedIssueQueryService.getIssueDistribution(scopedUserId);
    }

    public List<Map<String, Object>> getAssignableUsers() {
        return issueService.getAssignableUsers();
    }

    public Map<String, Object> getIssuePage(IssuePageQuery query) {
        return unifiedIssueQueryService.getIssuePage(
                query.scopedUserId(),
                query.page(),
                query.limit(),
                query.query(),
                query.status());
    }

    public Map<String, Object> getIssueDetail(Long scopedUserId, Long issueId) {
        return unifiedIssueQueryService.getIssueDetail(scopedUserId, issueId);
    }

    public Map<String, Object> updateIssueStatus(IssueStatusUpdateCommand command) {
        return issueService.updateIssueStatus(
                command.scopedUserId(),
                command.operatorId(),
                command.issueId(),
                command.status(),
                command.remark());
    }

    public Map<String, Object> updateIssueWorkflow(IssueWorkflowUpdateCommand command) {
        return issueService.updateIssueWorkflow(
                command.scopedUserId(),
                command.operatorId(),
                command.issueId(),
                command.requestBody());
    }
}
