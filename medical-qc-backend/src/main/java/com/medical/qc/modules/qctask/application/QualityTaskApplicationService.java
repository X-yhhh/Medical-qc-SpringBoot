package com.medical.qc.modules.qctask.application;

import com.medical.qc.modules.qctask.application.command.QualityTaskSubmitCommand;
import com.medical.qc.modules.unified.application.UnifiedQcTaskQueryService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

/**
 * 质控任务应用服务。
 *
 * <p>提交走统一模型写侧，查询走统一模型读侧。</p>
 */
@Service
public class QualityTaskApplicationService {
    private final MockQualityTaskServiceImpl mockQualityTaskService;
    private final UnifiedQcTaskQueryService unifiedQcTaskQueryService;

    public QualityTaskApplicationService(MockQualityTaskServiceImpl mockQualityTaskService,
                                         UnifiedQcTaskQueryService unifiedQcTaskQueryService) {
        this.mockQualityTaskService = mockQualityTaskService;
        this.unifiedQcTaskQueryService = unifiedQcTaskQueryService;
    }

    public Map<String, Object> submitTask(QualityTaskSubmitCommand command) throws IOException {
        return mockQualityTaskService.submitTask(
                command.taskType(),
                command.file(),
                command.patientName(),
                command.examId(),
                command.sourceMode(),
                command.user());
    }

    public Map<String, Object> getTaskDetail(String taskId, Long scopedUserId) {
        return unifiedQcTaskQueryService.getTaskDetail(taskId, scopedUserId);
    }

    public Map<String, Object> getTaskPage(Long scopedUserId,
                                           int page,
                                           int limit,
                                           String query,
                                           String taskType,
                                           String status,
                                           String sourceMode) {
        return unifiedQcTaskQueryService.getTaskPage(scopedUserId, page, limit, query, taskType, status, sourceMode);
    }
}
