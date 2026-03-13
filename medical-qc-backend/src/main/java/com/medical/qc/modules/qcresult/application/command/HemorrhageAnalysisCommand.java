package com.medical.qc.modules.qcresult.application.command;

import com.medical.qc.modules.auth.persistence.entity.User;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

/**
 * 脑出血检测应用层命令。
 *
 * @param file 本地上传影像文件；PACS 模式下可为空
 * @param user 当前登录医生
 * @param patientName 患者姓名
 * @param patientCode 患者编号
 * @param examId 检查号
 * @param gender 性别
 * @param age 年龄
 * @param studyDate 检查日期
 * @param sourceMode 来源模式
 */
public record HemorrhageAnalysisCommand(MultipartFile file,
                                        User user,
                                        String patientName,
                                        String patientCode,
                                        String examId,
                                        String gender,
                                        Integer age,
                                        LocalDate studyDate,
                                        String sourceMode) {
}

