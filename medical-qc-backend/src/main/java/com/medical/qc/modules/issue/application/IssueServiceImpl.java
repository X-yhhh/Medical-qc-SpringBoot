package com.medical.qc.modules.issue.application;

import com.medical.qc.bean.IssueWorkflowUpdateReq;
import com.medical.qc.modules.unified.application.UnifiedIssueQueryService;
import com.medical.qc.modules.unified.application.support.UnifiedIssueWriteService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 异常工单服务实现。
 *
 * <p>写侧统一落到 `issue_tickets / issue_action_logs / issue_capa_records`，读侧统一走查询服务。</p>
 */
@Service
public class IssueServiceImpl {
    private final UnifiedIssueWriteService unifiedIssueWriteService;
    private final UnifiedIssueQueryService unifiedIssueQueryService;

    public IssueServiceImpl(UnifiedIssueWriteService unifiedIssueWriteService,
                            UnifiedIssueQueryService unifiedIssueQueryService) {
        this.unifiedIssueWriteService = unifiedIssueWriteService;
        this.unifiedIssueQueryService = unifiedIssueQueryService;
    }

    public void syncHemorrhageIssue(Long taskId) {
        unifiedIssueWriteService.syncHemorrhageIssue(taskId);
    }

    public void syncQualityTaskIssue(Long taskId) {
        unifiedIssueWriteService.syncQualityTaskIssue(taskId);
    }

    public long countPendingIssues(Long scopedUserId) {
        return unifiedIssueQueryService.countPendingIssues(scopedUserId);
    }

    public long countHighRiskIssues(Long scopedUserId) {
        return unifiedIssueQueryService.countHighRiskIssues(scopedUserId);
    }

    public List<Map<String, Object>> getRiskAlerts(Long scopedUserId, int limit) {
        return unifiedIssueQueryService.getRiskAlerts(scopedUserId, limit);
    }

    public List<Map<String, Object>> getAssignableUsers() {
        return unifiedIssueWriteService.getAssignableUsers();
    }

    public Map<String, Object> updateIssueStatus(Long scopedUserId,
                                                 Long operatorId,
                                                 Long issueId,
                                                 String status,
                                                 String remark) {
        unifiedIssueWriteService.updateIssueStatus(scopedUserId, operatorId, issueId, status, remark);
        return unifiedIssueQueryService.getIssueSummary(scopedUserId, issueId);
    }

    public Map<String, Object> updateIssueWorkflow(Long scopedUserId,
                                                   Long operatorId,
                                                   Long issueId,
                                                   IssueWorkflowUpdateReq request) {
        unifiedIssueWriteService.updateIssueWorkflow(scopedUserId, operatorId, issueId, request);
        return unifiedIssueQueryService.getIssueDetail(scopedUserId, issueId);
    }
}
