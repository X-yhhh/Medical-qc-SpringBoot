package com.medical.qc.modules.qctask.application.command;

import com.medical.qc.modules.auth.persistence.entity.User;
import org.springframework.web.multipart.MultipartFile;

/**
 * 异步质控任务提交命令。
 */
public record QualityTaskSubmitCommand(String taskType,
                                       MultipartFile file,
                                       String patientName,
                                       String examId,
                                       String sourceMode,
                                       User user) {
}

