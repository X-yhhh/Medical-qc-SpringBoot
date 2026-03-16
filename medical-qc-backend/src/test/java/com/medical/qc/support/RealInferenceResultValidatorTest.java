package com.medical.qc.support;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 真实模型结果校验测试。
 */
class RealInferenceResultValidatorTest {
    private final RealInferenceResultValidator validator = new RealInferenceResultValidator();

    @Test
    void shouldRejectStructuredResultWhenQcItemsAreMissing() {
        Map<String, Object> result = Map.of(
                "summary", Map.of(
                        "totalItems", 0,
                        "qualityScore", 100,
                        "result", "合格"));

        assertThatThrownBy(() -> validator.validateStructuredResult("head", result))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未返回任何质控项");
    }

    @Test
    void shouldRejectStructuredResultWhenStatusIsIllegal() {
        Map<String, Object> result = Map.of(
                "qcItems", List.of(
                        Map.of("name", "运动伪影", "status", "异常")),
                "summary", Map.of(
                        "totalItems", 1,
                        "qualityScore", 0,
                        "result", "不合格"));

        assertThatThrownBy(() -> validator.validateStructuredResult("head", result))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("状态非法");
    }

    @Test
    void shouldAcceptNormalizedStructuredResultForRealInferenceTask() {
        Map<String, Object> result = Map.of(
                "qcItems", List.of(
                        Map.of("name", "运动伪影", "status", "不合格"),
                        Map.of("name", "扫描范围", "status", "合格")));

        Map<String, Object> validated = validator.validateStructuredResult("head", result);

        assertThat(MockQualityAnalysisSupport.resolveQcStatus(validated)).isEqualTo("不合格");
        assertThat(MockQualityAnalysisSupport.resolveAbnormalCount(validated)).isEqualTo(1);
        assertThat(MockQualityAnalysisSupport.resolveQualityScore(validated)).isEqualTo(50D);
    }

    @Test
    void shouldRejectHemorrhageRawResultWhenPredictionFieldsAreMissing() {
        Map<String, Object> rawResult = Map.of(
                "hemorrhage_probability", 0.2D,
                "no_hemorrhage_probability", 0.8D,
                "midline_shift", false,
                "ventricle_issue", false);

        assertThatThrownBy(() -> validator.validateRawHemorrhageResult(rawResult))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("推理结论");
    }
}
