package com.medical.qc.modules.qcresult.application.support;

import java.time.LocalDate;

/**
 * 脑出血检测前置准备结果。
 */
public record HemorrhagePreparedContext(String sourceMode,
                                        String analysisImagePath,
                                        String savedImagePath,
                                        String patientName,
                                        String patientCode,
                                        String gender,
                                        Integer age,
                                        LocalDate studyDate,
                                        String scannerModel) {
}

