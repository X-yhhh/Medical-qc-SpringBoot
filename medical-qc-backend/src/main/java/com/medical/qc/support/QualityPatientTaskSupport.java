package com.medical.qc.support;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 患者信息管理任务类型支持类。
 *
 * <p>负责统一维护五个质控项的任务类型与中文名称，
 * 避免控制层、服务层和 PACS 逻辑重复书写硬编码。</p>
 */
public final class QualityPatientTaskSupport {
    public static final String TASK_TYPE_HEAD = "head";
    public static final String TASK_TYPE_HEMORRHAGE = "hemorrhage";
    public static final String TASK_TYPE_CHEST_NON_CONTRAST = "chest-non-contrast";
    public static final String TASK_TYPE_CHEST_CONTRAST = "chest-contrast";
    public static final String TASK_TYPE_CORONARY_CTA = "coronary-cta";

    private static final Map<String, String> TASK_LABEL_MAP;

    static {
        Map<String, String> taskLabelMap = new LinkedHashMap<>();
        taskLabelMap.put(TASK_TYPE_HEAD, "CT头部平扫患者信息");
        taskLabelMap.put(TASK_TYPE_HEMORRHAGE, "头部出血检测患者信息");
        taskLabelMap.put(TASK_TYPE_CHEST_NON_CONTRAST, "CT胸部平扫患者信息");
        taskLabelMap.put(TASK_TYPE_CHEST_CONTRAST, "CT胸部增强患者信息");
        taskLabelMap.put(TASK_TYPE_CORONARY_CTA, "冠脉CTA患者信息");
        TASK_LABEL_MAP = Collections.unmodifiableMap(taskLabelMap);
    }

    private QualityPatientTaskSupport() {
    }

    /**
     * 判断当前任务类型是否受支持。
     *
     * @param taskType 任务类型
     * @return 是否受支持
     */
    public static boolean isSupportedTaskType(String taskType) {
        return TASK_LABEL_MAP.containsKey(normalizeTaskType(taskType));
    }

    /**
     * 统一清洗任务类型字符串。
     *
     * @param taskType 原始任务类型
     * @return 小写且去首尾空白后的任务类型；为空时返回空字符串
     */
    public static String normalizeTaskType(String taskType) {
        return taskType == null ? "" : taskType.trim().toLowerCase();
    }

    /**
     * 解析任务对应的中文名称。
     *
     * @param taskType 任务类型
     * @return 中文名称；不支持时返回原任务类型
     */
    public static String resolveTaskLabel(String taskType) {
        String normalizedTaskType = normalizeTaskType(taskType);
        return TASK_LABEL_MAP.getOrDefault(normalizedTaskType, normalizedTaskType);
    }
}

