package com.medical.qc.support;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 质控项源数据表路由支持类。
 *
 * <p>负责把 taskType 映射到独立的本地缓存表和 PACS 缓存表。</p>
 */
@Component
public class TaskScopedSourceTableSupport {
    private static final Map<String, String> PATIENT_INFO_TABLE_MAP = Map.of(
            QualityPatientTaskSupport.TASK_TYPE_HEAD, "head_patient_info",
            QualityPatientTaskSupport.TASK_TYPE_HEMORRHAGE, "hemorrhage_patient_info",
            QualityPatientTaskSupport.TASK_TYPE_CHEST_NON_CONTRAST, "chest_non_contrast_patient_info",
            QualityPatientTaskSupport.TASK_TYPE_CHEST_CONTRAST, "chest_contrast_patient_info",
            QualityPatientTaskSupport.TASK_TYPE_CORONARY_CTA, "coronary_cta_patient_info"
    );

    private static final Map<String, String> PACS_TABLE_MAP = Map.of(
            QualityPatientTaskSupport.TASK_TYPE_HEAD, "head_pacs_study_cache",
            QualityPatientTaskSupport.TASK_TYPE_HEMORRHAGE, "hemorrhage_pacs_study_cache",
            QualityPatientTaskSupport.TASK_TYPE_CHEST_NON_CONTRAST, "chest_non_contrast_pacs_study_cache",
            QualityPatientTaskSupport.TASK_TYPE_CHEST_CONTRAST, "chest_contrast_pacs_study_cache",
            QualityPatientTaskSupport.TASK_TYPE_CORONARY_CTA, "coronary_cta_pacs_study_cache"
    );

    /**
     * 返回任务专属患者缓存表名。
     */
    public String resolvePatientInfoTable(String taskType) {
        String normalizedTaskType = QualityPatientTaskSupport.normalizeTaskType(taskType);
        String tableName = PATIENT_INFO_TABLE_MAP.get(normalizedTaskType);
        if (tableName == null) {
            throw new IllegalArgumentException("不支持的患者缓存任务类型: " + taskType);
        }
        return tableName;
    }

    /**
     * 返回任务专属 PACS 缓存表名。
     */
    public String resolvePacsTable(String taskType) {
        String normalizedTaskType = QualityPatientTaskSupport.normalizeTaskType(taskType);
        String tableName = PACS_TABLE_MAP.get(normalizedTaskType);
        if (tableName == null) {
            throw new IllegalArgumentException("不支持的 PACS 缓存任务类型: " + taskType);
        }
        return tableName;
    }

    /**
     * 返回任务专属本地缓存表展示标签。
     */
    public String resolvePatientInfoTableLabel(String taskType) {
        return resolvePatientInfoTable(taskType);
    }

    /**
     * 返回任务专属 PACS 缓存表展示标签。
     */
    public String resolvePacsTableLabel(String taskType) {
        return resolvePacsTable(taskType);
    }
}
