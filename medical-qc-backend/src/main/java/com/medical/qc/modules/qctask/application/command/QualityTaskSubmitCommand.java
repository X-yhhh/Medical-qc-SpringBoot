package com.medical.qc.modules.qctask.application.command;

import com.medical.qc.modules.auth.persistence.entity.User;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Map;

/**
 * 异步质控任务提交命令。
 *
 * @param taskType 质控任务类型编码
 * @param file 本地上传场景下的影像文件
 * @param patientName 患者姓名
 * @param examId 检查号
 * @param patientId 患者编号，可来自人工录入或 PACS 回填
 * @param gender 性别
 * @param age 年龄
 * @param studyDate 检查日期
 * @param sourceMode 来源模式，local 或 pacs
 * @param metadata 任务专属附加元数据
 * @param user 当前提交任务的登录用户
 */
public record QualityTaskSubmitCommand(String taskType,
                                       MultipartFile file,
                                       String patientName,
                                       String examId,
                                       String patientId,
                                       String gender,
                                       Integer age,
                                       LocalDate studyDate,
                                       String sourceMode,
                                       Map<String, Object> metadata,
                                       User user) {
}

