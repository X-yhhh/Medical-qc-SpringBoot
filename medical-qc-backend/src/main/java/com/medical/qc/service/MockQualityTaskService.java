package com.medical.qc.service;

import com.medical.qc.entity.User;
import com.medical.qc.messaging.MockQualityTaskMessage;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * mock 质控异步任务服务。
 */
public interface MockQualityTaskService {
    Map<String, Object> submitTask(String taskType,
                                   MultipartFile file,
                                   String patientName,
                                   String examId,
                                   String sourceMode,
                                   User user) throws IOException;

    Map<String, Object> getTaskPage(Long scopedUserId,
                                    int page,
                                    int limit,
                                    String query,
                                    String taskType,
                                    String status,
                                    String sourceMode);

    Map<String, Object> getTaskDetail(String taskId, Long currentUserId);

    void processTask(MockQualityTaskMessage message);
}
