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
    // 所有工单写操作统一汇总到统一模型写服务。
    private final UnifiedIssueWriteService unifiedIssueWriteService;
    // 列表、统计和详情查询统一复用查询服务，避免读写口径分裂。
    private final UnifiedIssueQueryService unifiedIssueQueryService;

    public IssueServiceImpl(UnifiedIssueWriteService unifiedIssueWriteService,
                            UnifiedIssueQueryService unifiedIssueQueryService) {
        this.unifiedIssueWriteService = unifiedIssueWriteService;
        this.unifiedIssueQueryService = unifiedIssueQueryService;
    }

    /**
     * 根据脑出血检测结果同步异常工单。
     */
    public void syncHemorrhageIssue(Long taskId) {
        unifiedIssueWriteService.syncHemorrhageIssue(taskId);
    }

    /**
     * 根据异步质控任务结果同步异常工单。
     */
    public void syncQualityTaskIssue(Long taskId) {
        unifiedIssueWriteService.syncQualityTaskIssue(taskId);
    }

    /**
     * 统计待处理工单数量。
     */
    public long countPendingIssues(Long scopedUserId) {
        return unifiedIssueQueryService.countPendingIssues(scopedUserId);
    }

    /**
     * 统计高风险工单数量。
     */
    public long countHighRiskIssues(Long scopedUserId) {
        return unifiedIssueQueryService.countHighRiskIssues(scopedUserId);
    }

    /**
     * 查询风险预警列表。
     */
    public List<Map<String, Object>> getRiskAlerts(Long scopedUserId, int limit) {
        return unifiedIssueQueryService.getRiskAlerts(scopedUserId, limit);
    }

    /**
     * 查询可指派的处理人员。
     */
    public List<Map<String, Object>> getAssignableUsers() {
        return unifiedIssueWriteService.getAssignableUsers();
    }

    /**
     * 更新工单状态并回查最新摘要，供前端立即刷新表格。
     */
    public Map<String, Object> updateIssueStatus(Long scopedUserId,
                                                 Long operatorId,
                                                 Long issueId,
                                                 String status,
                                                 String remark) {
        // 写操作先落库，再回查最新摘要，保证返回值包含最新状态和 SLA 信息。
        unifiedIssueWriteService.updateIssueStatus(scopedUserId, operatorId, issueId, status, remark);
        return unifiedIssueQueryService.getIssueSummary(scopedUserId, issueId);
    }

    /**
     * 更新工单工作流并回查完整详情，供前端详情弹窗即时回显。
     */
    public Map<String, Object> updateIssueWorkflow(Long scopedUserId,
                                                   Long operatorId,
                                                   Long issueId,
                                                   IssueWorkflowUpdateReq request) {
        // CAPA、指派和状态可能同时变化，因此这里统一走工作流更新入口。
        unifiedIssueWriteService.updateIssueWorkflow(scopedUserId, operatorId, issueId, request);
        return unifiedIssueQueryService.getIssueDetail(scopedUserId, issueId);
    }
}
