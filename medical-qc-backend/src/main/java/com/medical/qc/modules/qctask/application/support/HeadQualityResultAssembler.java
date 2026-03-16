package com.medical.qc.modules.qctask.application.support;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 头部 CT 平扫质控结果装配组件。
 *
 * <p>负责把 Python 推理结果补齐为前端和统一结果表都可直接消费的结构。</p>
 */
@Component
public class HeadQualityResultAssembler {
    private static final String MODEL_CODE = "head_ct_plain_qc";
    private static final String MODEL_VERSION = "head_ct_plain_qc_multitask_model_v1";

    /**
     * 补齐头部平扫质控结果的统一字段。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> enrichResult(Map<String, Object> predictionResult,
                                            HeadQualityPreparedContext context) {
        Map<String, Object> result = predictionResult == null
                ? new HashMap<>()
                : new HashMap<>(predictionResult);

        Map<String, Object> patientInfo = result.get("patientInfo") instanceof Map<?, ?> patientInfoMap
                ? new HashMap<>((Map<String, Object>) patientInfoMap)
                : new HashMap<>();
        patientInfo.put("name", firstNonBlank(context.patientName(), patientInfo.get("name")));
        patientInfo.put("studyId", firstNonBlank(context.examId(), patientInfo.get("studyId")));
        patientInfo.put("accessionNumber", firstNonBlank(context.examId(), patientInfo.get("accessionNumber")));
        patientInfo.put("gender", firstNonBlank(context.gender(), patientInfo.get("gender"), "未知"));
        patientInfo.put("age", context.age() == null ? patientInfo.getOrDefault("age", 0) : context.age());
        patientInfo.put("studyDate", context.studyDate() == null
                ? patientInfo.getOrDefault("studyDate", "")
                : context.studyDate().toString());
        patientInfo.put("device", firstNonBlank(context.scannerModel(), patientInfo.get("device")));
        patientInfo.put("sourceMode", context.sourceMode());
        patientInfo.put("originalFilename", firstNonBlank(context.originalFilename(), patientInfo.get("originalFilename")));
        result.put("patientInfo", patientInfo);

        List<Map<String, Object>> qcItems = normalizeQcItems(result.get("qcItems"));
        result.put("qcItems", qcItems);

        Map<String, Object> summary = result.get("summary") instanceof Map<?, ?> summaryMap
                ? new HashMap<>((Map<String, Object>) summaryMap)
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

        result.put("taskType", "head");
        result.put("taskTypeName", "CT头部平扫质控");
        result.put("mock", false);
        // 真实推理链路统一暴露分析模式，便于任务中心和页面显式区分 real/mock。
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
     * 规范化质控项结构，补齐阈值和概率字段格式。
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> normalizeQcItems(Object rawQcItems) {
        if (!(rawQcItems instanceof List<?> rawList)) {
            return List.of();
        }

        List<Map<String, Object>> normalizedItems = new ArrayList<>();
        for (Object rawItem : rawList) {
            if (!(rawItem instanceof Map<?, ?> rawMap)) {
                continue;
            }

            Map<String, Object> item = new HashMap<>((Map<String, Object>) rawMap);
            item.put("name", firstNonBlank(item.get("name"), "质控项"));
            item.put("key", firstNonBlank(item.get("key"), item.get("name")));
            item.put("status", firstNonBlank(item.get("status"), "合格"));
            item.put("description", firstNonBlank(item.get("description"), ""));
            item.put("detail", "不合格".equals(item.get("status")) ? firstNonBlank(item.get("detail"), "") : "");

            Object threshold = item.get("threshold");
            if (threshold instanceof Number number) {
                item.put("threshold", round(number.doubleValue(), 4));
            }

            Object failProbability = item.get("failProbability");
            if (failProbability instanceof Number number) {
                item.put("failProbability", round(number.doubleValue(), 4));
                item.put("passProbability", round(1.0D - number.doubleValue(), 4));
            }

            normalizedItems.add(item);
        }
        return normalizedItems;
    }

    /**
     * 解析主异常项。
     */
    private String resolvePrimaryIssue(List<Map<String, Object>> qcItems) {
        for (Map<String, Object> item : qcItems) {
            if ("不合格".equals(item.get("status"))) {
                return firstNonBlank(item.get("name"), "未见明显异常");
            }
        }
        return "未见明显异常";
    }

    /**
     * 保留四位小数，便于前端和数据库展示。
     */
    private double round(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * 从多个候选值中返回第一个非空字符串。
     */
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
