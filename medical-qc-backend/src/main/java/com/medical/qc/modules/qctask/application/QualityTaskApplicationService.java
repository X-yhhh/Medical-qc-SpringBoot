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
    // 提交与状态迁移逻辑全部由任务服务实现承担。
    private final MockQualityTaskServiceImpl mockQualityTaskService;
    // 任务列表与详情查询统一走统一模型读服务。
    private final UnifiedQcTaskQueryService unifiedQcTaskQueryService;

    public QualityTaskApplicationService(MockQualityTaskServiceImpl mockQualityTaskService,
                                         UnifiedQcTaskQueryService unifiedQcTaskQueryService) {
        this.mockQualityTaskService = mockQualityTaskService;
        this.unifiedQcTaskQueryService = unifiedQcTaskQueryService;
    }

    /**
     * 提交异步质控任务。
     */
    public Map<String, Object> submitTask(QualityTaskSubmitCommand command) throws IOException {
        return mockQualityTaskService.submitTask(
                command.taskType(),
                command.file(),
                command.patientName(),
                command.examId(),
                command.sourceMode(),
                command.user());
    }

    /**
     * 查询单个任务详情。
     */
    public Map<String, Object> getTaskDetail(String taskId, Long scopedUserId) {
        return unifiedQcTaskQueryService.getTaskDetail(taskId, scopedUserId);
    }

    /**
     * 查询任务分页列表。
     */
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
