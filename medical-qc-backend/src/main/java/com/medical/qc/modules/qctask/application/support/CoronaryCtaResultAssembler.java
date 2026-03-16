package com.medical.qc.modules.qctask.application.support;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 冠脉CTA质控结果装配组件。
 */
@Component
public class CoronaryCtaResultAssembler {
    private static final String MODEL_CODE = "coronary_cta_qc_rule_v1";
    private static final String MODEL_VERSION = "rules-2026.03";

    @SuppressWarnings("unchecked")
    public Map<String, Object> enrichResult(Map<String, Object> predictionResult,
                                            CoronaryCtaPreparedContext context) {
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
        patientInfo.put("heartRate", context.heartRate() == null ? patientInfo.get("heartRate") : context.heartRate());
        patientInfo.put("hrVariability", context.hrVariability() == null ? patientInfo.get("hrVariability") : context.hrVariability());
        patientInfo.put("reconPhase", firstNonBlank(context.reconPhase(), patientInfo.get("reconPhase")));
        patientInfo.put("kVp", firstNonBlank(context.kVp(), patientInfo.get("kVp")));
        patientInfo.put("sliceThickness", context.sliceThickness() == null ? patientInfo.get("sliceThickness") : context.sliceThickness());
        result.put("patientInfo", patientInfo);

        result.put("taskType", "coronary-cta");
        result.put("taskTypeName", "冠脉CTA质控");
        // 冠脉 CTA 本轮固定为 mock 规则链路，不再伪装成真实模型推理。
        result.put("mock", true);
        result.put("analysisMode", "mock-rule-based");
        result.put("analysisLabel", "模拟分析（规则辅助）");
        result.put("deterministic", true);
        result.put("modelCode", MODEL_CODE);
        result.put("modelVersion", MODEL_VERSION);
        return result;
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
