package com.medical.qc.support;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 真实模型结果校验组件。
 *
 * <p>该组件只服务三条真实推理链路：脑出血检测、CT 头部平扫、CT 胸部平扫。</p>
 * <p>核心目标是把“模型返回空结果/错误结果却被系统误判为合格”的风险前置拦截。</p>
 */
@Component
public class RealInferenceResultValidator {
    // 当前系统接受的标准质控项状态集合。
    private static final Set<String> ALLOWED_QC_ITEM_STATUSES = Set.of("合格", "不合格", "待人工确认");

    /**
     * 校验真实推理链路的结构化结果。
     *
     * <p>调用方应在结果装配完成后再执行校验，这样 patientInfo/qcItems/summary 的结构已经统一。</p>
     *
     * @param taskType 任务类型编码
     * @param rawResult 装配后的结构化结果
     * @return 标准化后的结果载荷
     */
    public Map<String, Object> validateStructuredResult(String taskType, Map<String, Object> rawResult) {
        String taskTypeName = MockQualityAnalysisSupport.resolveTaskTypeName(taskType);

        if (rawResult == null || rawResult.isEmpty()) {
            throw new IllegalStateException(taskTypeName + " 模型返回空结果，任务已中止");
        }

        if (rawResult.containsKey("error")) {
            throw new IllegalStateException(taskTypeName + " 模型推理失败：" + String.valueOf(rawResult.get("error")));
        }

        // 所有真实链路结果都先归一化，再基于统一结构做完整性校验。
        Map<String, Object> normalizedResult = MockQualityAnalysisSupport.normalizeResultPayload(rawResult);
        if (normalizedResult.isEmpty()) {
            throw new IllegalStateException(taskTypeName + " 模型返回结果为空，任务已中止");
        }

        List<Map<String, Object>> qcItems = MockQualityAnalysisSupport.extractQcItems(normalizedResult);
        if (qcItems.isEmpty()) {
            throw new IllegalStateException(taskTypeName + " 模型未返回任何质控项，任务已中止");
        }

        for (Map<String, Object> qcItem : qcItems) {
            validateQcItem(taskTypeName, qcItem);
        }

        Map<String, Object> summary = MockQualityAnalysisSupport.extractSummary(normalizedResult);
        if (summary.isEmpty()) {
            throw new IllegalStateException(taskTypeName + " 结果摘要缺失，任务已中止");
        }

        int totalItems = MockQualityAnalysisSupport.resolveTotalCount(normalizedResult);
        if (totalItems <= 0) {
            throw new IllegalStateException(taskTypeName + " 结果摘要总项数无效，任务已中止");
        }

        double qualityScore = MockQualityAnalysisSupport.resolveQualityScore(normalizedResult);
        if (Double.isNaN(qualityScore) || Double.isInfinite(qualityScore) || qualityScore < 0D || qualityScore > 100D) {
            throw new IllegalStateException(taskTypeName + " 结果评分超出允许范围，任务已中止");
        }

        String qcStatus = MockQualityAnalysisSupport.resolveQcStatus(normalizedResult);
        if (!StringUtils.hasText(qcStatus)) {
            throw new IllegalStateException(taskTypeName + " 结果结论缺失，任务已中止");
        }

        return normalizedResult;
    }

    /**
     * 校验脑出血模型的原始返回结构。
     *
     * <p>脑出血链路会先拿到 prediction/probability 这种原始字段，再装配成统一 qcItems 结构。</p>
     * <p>因此需要在装配前先拦住“字段缺失却被默认映射为未出血”的情况。</p>
     *
     * @param rawResult Python 模型直接返回的原始结果
     */
    public void validateRawHemorrhageResult(Map<String, Object> rawResult) {
        String taskTypeName = MockQualityAnalysisSupport.resolveTaskTypeName(MockQualityAnalysisSupport.TASK_TYPE_HEMORRHAGE);

        if (rawResult == null || rawResult.isEmpty()) {
            throw new IllegalStateException(taskTypeName + " 模型返回空结果，任务已中止");
        }

        if (rawResult.containsKey("error")) {
            throw new IllegalStateException(taskTypeName + " 模型推理失败：" + String.valueOf(rawResult.get("error")));
        }

        validateRequiredText(taskTypeName, rawResult, "prediction", "推理结论");
        validateProbability(taskTypeName, rawResult, "hemorrhage_probability", "出血概率");
        validateProbability(taskTypeName, rawResult, "no_hemorrhage_probability", "非出血概率");
        validateRequiredBoolean(taskTypeName, rawResult, "midline_shift", "中线偏移标记");
        validateRequiredBoolean(taskTypeName, rawResult, "ventricle_issue", "脑室异常标记");
    }

    /**
     * 校验单个质控项的名称和状态。
     */
    private void validateQcItem(String taskTypeName, Map<String, Object> qcItem) {
        if (qcItem == null || qcItem.isEmpty()) {
            throw new IllegalStateException(taskTypeName + " 存在空质控项，任务已中止");
        }

        String name = qcItem.get("name") == null ? null : String.valueOf(qcItem.get("name")).trim();
        if (!StringUtils.hasText(name)) {
            throw new IllegalStateException(taskTypeName + " 存在缺少名称的质控项，任务已中止");
        }

        String status = qcItem.get("status") == null ? null : String.valueOf(qcItem.get("status")).trim();
        if (!ALLOWED_QC_ITEM_STATUSES.contains(status)) {
            throw new IllegalStateException(taskTypeName + " 质控项 [" + name + "] 的状态非法，任务已中止");
        }
    }

    /**
     * 校验必填文本字段。
     */
    private void validateRequiredText(String taskTypeName,
                                      Map<String, Object> result,
                                      String fieldKey,
                                      String fieldLabel) {
        String text = result.get(fieldKey) == null ? null : String.valueOf(result.get(fieldKey)).trim();
        if (!StringUtils.hasText(text)) {
            throw new IllegalStateException(taskTypeName + " 缺少必填字段：" + fieldLabel);
        }
    }

    /**
     * 校验 0-1 区间概率字段。
     */
    private void validateProbability(String taskTypeName,
                                     Map<String, Object> result,
                                     String fieldKey,
                                     String fieldLabel) {
        Object value = result.get(fieldKey);
        if (!(value instanceof Number number)) {
            throw new IllegalStateException(taskTypeName + " 缺少有效字段：" + fieldLabel);
        }
        double probability = number.doubleValue();
        if (Double.isNaN(probability) || Double.isInfinite(probability) || probability < 0D || probability > 1D) {
            throw new IllegalStateException(taskTypeName + " 字段 [" + fieldLabel + "] 超出允许范围");
        }
    }

    /**
     * 校验必填布尔字段。
     */
    private void validateRequiredBoolean(String taskTypeName,
                                         Map<String, Object> result,
                                         String fieldKey,
                                         String fieldLabel) {
        if (!(result.get(fieldKey) instanceof Boolean)) {
            throw new IllegalStateException(taskTypeName + " 缺少有效字段：" + fieldLabel);
        }
    }
}
