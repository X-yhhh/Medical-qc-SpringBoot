package com.medical.qc.modules.qcresult.application.command;

import com.medical.qc.modules.auth.persistence.entity.User;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

/**
 * 脑出血检测应用层命令。
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

