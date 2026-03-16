package com.medical.qc.modules.qctask.application;

import com.medical.qc.modules.auth.persistence.entity.User;
import com.medical.qc.modules.issue.application.IssueServiceImpl;
import com.medical.qc.modules.qctask.application.command.QualityTaskBatchReviewCommand;
import com.medical.qc.modules.qctask.application.command.QualityTaskBatchRetryCommand;
import com.medical.qc.modules.qctask.application.command.QualityTaskRepairCommand;
import com.medical.qc.modules.qctask.application.command.QualityTaskReviewCommand;
import com.medical.qc.modules.qctask.application.support.QualityTaskReportService;
import com.medical.qc.modules.qctask.model.QcTaskRecord;
import com.medical.qc.modules.unified.application.UnifiedQcTaskQueryService;
import com.medical.qc.modules.unified.application.support.UnifiedQualityTaskWriteService;
import com.medical.qc.support.MockQualityAnalysisSupport;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 质控任务工作流应用服务。
 */
@Service
public class QualityTaskWorkflowApplicationService {
    private final MockQualityTaskServiceImpl mockQualityTaskService;
    private final UnifiedQcTaskQueryService unifiedQcTaskQueryService;
    private final UnifiedQualityTaskWriteService unifiedQualityTaskWriteService;
    private final QualityTaskReportService qualityTaskReportService;
    private final IssueServiceImpl issueService;

    public QualityTaskWorkflowApplicationService(MockQualityTaskServiceImpl mockQualityTaskService,
                                                 UnifiedQcTaskQueryService unifiedQcTaskQueryService,
                                                 UnifiedQualityTaskWriteService unifiedQualityTaskWriteService,
                                                 QualityTaskReportService qualityTaskReportService,
                                                 IssueServiceImpl issueService) {
        this.mockQualityTaskService = mockQualityTaskService;
        this.unifiedQcTaskQueryService = unifiedQcTaskQueryService;
        this.unifiedQualityTaskWriteService = unifiedQualityTaskWriteService;
        this.qualityTaskReportService = qualityTaskReportService;
        this.issueService = issueService;
    }

    /**
     * 更新单条任务的人工复核状态。
     */
    public Map<String, Object> updateReview(String taskId,
                                            Long scopedUserId,
                                            Long operatorId,
                                            QualityTaskReviewCommand command) {
        unifiedQualityTaskWriteService.updateReview(
                taskId,
                scopedUserId,
                operatorId,
                command.reviewStatus(),
                command.reviewComment(),
                Boolean.TRUE.equals(command.lockResult()),
                command.externalRef());
        return unifiedQcTaskQueryService.getTaskDetail(taskId, scopedUserId);
    }

    /**
     * 批量更新人工复核状态。
     */
    public Map<String, Object> batchUpdateReview(Long scopedUserId,
                                                 Long operatorId,
                                                 QualityTaskBatchReviewCommand command) {
        List<Map<String, Object>> updatedItems = new ArrayList<>();
        List<String> taskIds = command == null || command.taskIds() == null ? List.of() : command.taskIds();
        for (String taskId : taskIds) {
            updatedItems.add(updateReview(
                    taskId,
                    scopedUserId,
                    operatorId,
                    new QualityTaskReviewCommand(
                            command.reviewStatus(),
                            command.reviewComment(),
                            command.lockResult(),
                            null)));
        }
        return Map.of(
                "count", updatedItems.size(),
                "items", updatedItems);
    }

    /**
     * 批量重跑质控任务。
     */
    public Map<String, Object> batchRetry(User user, QualityTaskBatchRetryCommand command) {
        List<Map<String, Object>> submitResults = new ArrayList<>();
        List<String> taskIds = command == null || command.taskIds() == null ? List.of() : command.taskIds();
        for (String taskId : taskIds) {
            submitResults.add(mockQualityTaskService.retryTask(taskId, user));
        }
        return Map.of(
                "count", submitResults.size(),
                "items", submitResults);
    }

    /**
     * 导出单条任务的 DOCX 报告。
     */
    public byte[] exportTaskReport(String taskId, Long scopedUserId) throws IOException {
        Map<String, Object> taskDetail = unifiedQcTaskQueryService.getTaskDetail(taskId, scopedUserId);
        return qualityTaskReportService.buildTaskReportDocx(taskDetail);
    }

    /**
     * 导出多条任务的 CSV 摘要。
     */
    public byte[] exportTaskCsv(List<String> taskIds, Long scopedUserId) {
        List<Map<String, Object>> taskItems = new ArrayList<>();
        for (String taskId : taskIds) {
            taskItems.add(unifiedQcTaskQueryService.getTaskDetail(taskId, scopedUserId));
        }
        return qualityTaskReportService.buildTaskCsv(taskItems);
    }

    /**
     * 计算任务中心概览指标。
     */
    public Map<String, Object> getTaskMetrics(Long scopedUserId) {
        List<QcTaskRecord> taskRecords = unifiedQcTaskQueryService.getTaskRecords(scopedUserId);
        long totalCount = taskRecords.size();
        long successCount = taskRecords.stream().filter(item -> "SUCCESS".equals(item.getTaskStatus())).count();
        long failedCount = taskRecords.stream().filter(item -> "FAILED".equals(item.getTaskStatus())).count();
        long reviewPendingCount = taskRecords.stream()
                .filter(item -> "PENDING".equals(item.getReviewStatus()))
                .count();
        double averageScore = taskRecords.stream()
                .filter(item -> item.getQualityScore() != null)
                .mapToDouble(item -> item.getQualityScore().doubleValue())
                .average()
                .orElse(0.0D);

        Map<String, Object> response = new HashMap<>();
        response.put("totalCount", totalCount);
        response.put("successCount", successCount);
        response.put("failedCount", failedCount);
        response.put("reviewPendingCount", reviewPendingCount);
        response.put("successRate", totalCount == 0 ? 0.0D : Math.round(successCount * 1000.0D / totalCount) / 10.0D);
        response.put("averageQualityScore", Math.round(averageScore * 10.0D) / 10.0D);
        return response;
    }

    /**
     * 修复历史任务结果中的口径不一致问题，并按需刷新异常工单。
     */
    public Map<String, Object> repairTaskResults(QualityTaskRepairCommand command, Long operatorId) {
        List<String> taskIds = command == null || command.taskIds() == null ? List.of() : command.taskIds();
        boolean refreshIssues = command != null && Boolean.TRUE.equals(command.refreshIssues());

        Map<String, Object> repairSummary = unifiedQualityTaskWriteService.repairHistoricalTaskResults(taskIds, operatorId);
        if (!refreshIssues) {
            return repairSummary;
        }

        Set<String> scopedTaskIds = taskIds == null
                ? Set.of()
                : taskIds.stream()
                .filter(taskId -> taskId != null && !taskId.isBlank())
                .collect(Collectors.toSet());
        List<QcTaskRecord> refreshTargets = unifiedQcTaskQueryService.getTaskRecords(null).stream()
                .filter(item -> scopedTaskIds.isEmpty() || scopedTaskIds.contains(item.getTaskId()))
                .toList();
        int refreshedIssueCount = 0;
        for (QcTaskRecord item : refreshTargets) {
            if (item.getId() == null) {
                continue;
            }
            if (MockQualityAnalysisSupport.TASK_TYPE_HEMORRHAGE.equals(item.getTaskType())) {
                issueService.syncHemorrhageIssue(item.getId());
            } else {
                issueService.syncQualityTaskIssue(item.getId());
            }
            refreshedIssueCount += 1;
        }

        Map<String, Object> response = new HashMap<>(repairSummary);
        response.put("refreshedIssueCount", refreshedIssueCount);
        return response;
    }
}
