package com.medical.qc.modules.qctask.application.support;

import com.medical.qc.support.MockQualityAnalysisSupport;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则分析结果构建辅助类。
 *
 * <p>用于把规则判定统一转换为现有前端可消费的 patientInfo + qcItems + summary 结构。</p>
 */
public final class RuleAnalysisSupport {
    public static final String STATUS_PASS = "合格";
    public static final String STATUS_FAIL = "不合格";
    public static final String STATUS_REVIEW = "待人工确认";

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private RuleAnalysisSupport() {
    }

    public static Map<String, Object> createResultEnvelope(String taskType,
                                                           String modelCode,
                                                           String modelVersion,
                                                           Map<String, Object> patientInfo,
                                                           List<Map<String, Object>> qcItems,
                                                           long durationMs) {
        Map<String, Object> response = new HashMap<>();
        response.put("taskType", taskType);
        response.put("taskTypeName", MockQualityAnalysisSupport.resolveTaskTypeName(taskType));
        response.put("mock", false);
        response.put("analysisMode", "rule-based");
        response.put("analysisLabel", "规则辅助分析");
        response.put("deterministic", true);
        response.put("modelCode", modelCode);
        response.put("modelVersion", modelVersion);
        response.put("patientInfo", patientInfo == null ? Map.of() : patientInfo);
        response.put("qcItems", qcItems == null ? List.of() : qcItems);
        response.put("duration", Math.max(200L, durationMs));
        response.put("summary", buildSummary(qcItems == null ? List.of() : qcItems));
        return response;
    }

    public static Map<String, Object> createPatientInfo(String patientName,
                                                        String examId,
                                                        String sourceMode,
                                                        String originalFilename) {
        Map<String, Object> patientInfo = new LinkedHashMap<>();
        patientInfo.put("name", patientName);
        patientInfo.put("studyId", examId);
        patientInfo.put("accessionNumber", examId);
        patientInfo.put("studyDate", LocalDateTime.now().format(DATE_TIME_FORMATTER));
        patientInfo.put("sourceMode", sourceMode);
        patientInfo.put("sourceLabel", MockQualityAnalysisSupport.SOURCE_MODE_PACS.equals(sourceMode) ? "PACS 调取" : "本地上传");
        patientInfo.put("originalFilename", originalFilename);
        return patientInfo;
    }

    public static Map<String, Object> createQcItem(String itemCode,
                                                   String name,
                                                   String status,
                                                   String description,
                                                   String detail) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", itemCode);
        item.put("itemCode", itemCode);
        item.put("name", name);
        item.put("status", status);
        item.put("description", description);
        item.put("detail", detail == null ? "" : detail);
        return item;
    }

    public static Map<String, Object> createQcItem(String itemCode,
                                                   String name,
                                                   String status,
                                                   String description,
                                                   String detail,
                                                   String phase) {
        Map<String, Object> item = createQcItem(itemCode, name, status, description, detail);
        item.put("phase", phase);
        return item;
    }

    public static Map<String, Object> buildSummary(List<Map<String, Object>> qcItems) {
        int totalCount = qcItems == null ? 0 : qcItems.size();
        int passCount = 0;
        int failCount = 0;
        int reviewCount = 0;

        if (qcItems != null) {
            for (Map<String, Object> item : qcItems) {
                String status = normalizeText(item == null ? null : item.get("status"));
                if (STATUS_PASS.equals(status)) {
                    passCount += 1;
                } else if (STATUS_FAIL.equals(status)) {
                    failCount += 1;
                } else if (STATUS_REVIEW.equals(status)) {
                    reviewCount += 1;
                }
            }
        }

        int abnormalCount = failCount + reviewCount;
        int qualityScore = totalCount == 0 ? 0 : (int) Math.round(passCount * 100.0D / totalCount);
        String result = failCount > 0 ? STATUS_FAIL : (reviewCount > 0 ? STATUS_REVIEW : STATUS_PASS);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalItems", totalCount);
        summary.put("passCount", passCount);
        summary.put("failCount", failCount);
        summary.put("reviewCount", reviewCount);
        summary.put("abnormalCount", abnormalCount);
        summary.put("qualityScore", qualityScore);
        summary.put("result", result);
        return summary;
    }

    public static String normalizeText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() || "null".equalsIgnoreCase(text) ? null : text;
    }
}
