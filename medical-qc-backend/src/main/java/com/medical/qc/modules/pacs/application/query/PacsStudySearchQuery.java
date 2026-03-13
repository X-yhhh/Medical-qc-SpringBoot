package com.medical.qc.modules.pacs.application.query;

import java.time.LocalDate;

/**
 * PACS 检查查询条件。
 */
public record PacsStudySearchQuery(String taskType,
                                   String patientId,
                                   String patientName,
                                   String accessionNumber,
                                   LocalDate startDate,
                                   LocalDate endDate) {
}

