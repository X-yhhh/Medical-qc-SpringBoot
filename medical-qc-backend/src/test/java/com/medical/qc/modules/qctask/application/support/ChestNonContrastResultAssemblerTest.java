package com.medical.qc.modules.qctask.application.support;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 胸部平扫结果装配测试。
 */
class ChestNonContrastResultAssemblerTest {
    private final ChestNonContrastResultAssembler assembler = new ChestNonContrastResultAssembler();

    @Test
    void shouldBuildStableSummaryAndPrimaryIssueForChestNonContrastResult() {
        Map<String, Object> predictionResult = new HashMap<>();
        predictionResult.put("patientInfo", Map.of("name", "旧患者"));
        predictionResult.put("qcItems", List.of(
                Map.of("name", "扫描范围", "status", "合格", "description", "范围应完整"),
                Map.of("name", "呼吸配合", "status", "待人工确认", "description", "应避免明显呼吸伪影"),
                Map.of("name", "纵隔窗", "status", "不合格", "description", "纵隔窗应满足阅片要求")));

        ChestNonContrastPreparedContext context = new ChestNonContrastPreparedContext(
                "local",
                "F:/tmp/chest.nii.gz",
                "张三",
                "EXAM-3001",
                "男",
                54,
                LocalDate.of(2026, 3, 16),
                "GE Revolution CT",
                "chest.nii.gz");

        Map<String, Object> enriched = assembler.enrichResult(predictionResult, context);
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) enriched.get("summary");

        assertThat(enriched.get("mock")).isEqualTo(false);
        assertThat(enriched.get("analysisMode")).isEqualTo("real-model");
        assertThat(enriched.get("analysisLabel")).isEqualTo("真实模型推理");
        assertThat(enriched.get("qcStatus")).isEqualTo("不合格");
        assertThat(enriched.get("primaryIssue")).isEqualTo("纵隔窗");
        assertThat(enriched.get("abnormalCount")).isEqualTo(2);
        assertThat(enriched.get("qualityScore")).isEqualTo(33);
        assertThat(summary.get("failCount")).isEqualTo(1);
        assertThat(summary.get("reviewCount")).isEqualTo(1);
        assertThat(summary.get("result")).isEqualTo("不合格");
    }
}
