package com.medical.qc.modules.qctask.application.support;

import com.medical.qc.support.MockQualityAnalysisSupport;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CT胸部平扫质控结果装配组件。
 */
@Component
public class ChestNonContrastResultAssembler {
    private static final String MODEL_CODE = "chest_ct_non_contrast_qc";
    private static final String MODEL_VERSION = "chest_ct_non_contrast_qc_multitask_model_v1";

    @SuppressWarnings("unchecked")
    public Map<String, Object> enrichResult(Map<String, Object> predictionResult,
                                            ChestNonContrastPreparedContext context) {
        Map<String, Object> result = predictionResult == null ? new HashMap<>() : new HashMap<>(predictionResult);
        Map<String, Object> patientInfo = result.get("patientInfo") instanceof Map<?, ?> map
                ? new HashMap<>((Map<String, Object>) map)
                : new HashMap<>();

        patientInfo.put("name", firstNonBlank(context.patientName(), patientInfo.get("name")));
        patientInfo.put("studyId", firstNonBlank(context.examId(), patientInfo.get("studyId")));
        patientInfo.put("accessionNumber", firstNonBlank(context.examId(), patientInfo.get("accessionNumber")));
        patientInfo.put("gender", firstNonBlank(context.gender(), patientInfo.get("gender"), "未知"));
        patientInfo.put("age", context.age() == null ? patientInfo.getOrDefault("age", 0) : context.age());
        patientInfo.put("studyDate", context.studyDate() == null ? patientInfo.getOrDefault("studyDate", "") : context.studyDate().toString());
        patientInfo.put("device", firstNonBlank(context.scannerModel(), patientInfo.get("device")));
        patientInfo.put("sourceMode", context.sourceMode());
        patientInfo.put("originalFilename", firstNonBlank(context.originalFilename(), patientInfo.get("originalFilename")));
        result.put("patientInfo", patientInfo);

        List<Map<String, Object>> qcItems = MockQualityAnalysisSupport.extractQcItems(result);
        Map<String, Object> summary = result.get("summary") instanceof Map<?, ?> map
                ? new HashMap<>((Map<String, Object>) map)
                : new HashMap<>();
        int failCount = (int) qcItems.stream().filter(item -> "不合格".equals(item.get("status"))).count();
        int reviewCount = (int) qcItems.stream().filter(item -> "待人工确认".equals(item.get("status"))).count();
        int abnormalCount = failCount + reviewCount;
        int qualityScore = qcItems.isEmpty()
                ? 0
                : (int) Math.round((qcItems.size() - abnormalCount) * 100.0D / qcItems.size());
        summary.put("totalItems", qcItems.size());
        summary.put("abnormalCount", abnormalCount);
        summary.put("failCount", failCount);
        summary.put("reviewCount", reviewCount);
        summary.put("qualityScore", qualityScore);
        summary.put("result", failCount > 0 ? "不合格" : reviewCount > 0 ? "待人工确认" : "合格");
        result.put("summary", summary);

        result.put("taskType", "chest-non-contrast");
        result.put("taskTypeName", "CT胸部平扫质控");
        result.put("mock", false);
        result.put("analysisMode", "real-model");
        result.put("analysisLabel", "真实模型推理");
        result.put("modelCode", MODEL_CODE);
        result.put("modelVersion", MODEL_VERSION);
        result.put("primaryIssue", resolvePrimaryIssue(qcItems));
        result.put("qcStatus", summary.get("result"));
        result.put("qualityScore", qualityScore);
        result.put("abnormalCount", abnormalCount);
        return result;
    }

    /**
     * 解析主异常项，供任务中心和异常工单使用。
     */
    private String resolvePrimaryIssue(List<Map<String, Object>> qcItems) {
        for (Map<String, Object> qcItem : qcItems) {
            if ("不合格".equals(qcItem.get("status"))) {
                return firstNonBlank(qcItem.get("name"), "结果不完整");
            }
        }
        for (Map<String, Object> qcItem : qcItems) {
            if ("待人工确认".equals(qcItem.get("status"))) {
                return firstNonBlank(qcItem.get("name"), "结果不完整");
            }
        }
        return "未见明显异常";
    }

    private String firstNonBlank(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).trim();
            if (!text.isEmpty() && !"null".equalsIgnoreCase(text)) {
                return text;
            }
        }
        return null;
    }
}
