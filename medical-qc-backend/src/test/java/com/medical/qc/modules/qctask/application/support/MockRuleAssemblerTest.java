package com.medical.qc.modules.qctask.application.support;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 规则型 mock 结果装配测试。
 */
class MockRuleAssemblerTest {

    @Test
    void chestContrastAssemblerShouldExposeMockRuleMetadata() {
        ChestContrastResultAssembler assembler = new ChestContrastResultAssembler();
        ChestContrastPreparedContext context = new ChestContrastPreparedContext(
                "local",
                "F:/tmp/chest-contrast.nii.gz",
                "张三",
                "EXAM-4001",
                "女",
                48,
                LocalDate.of(2026, 3, 16),
                "Siemens Force",
                "contrast.nii.gz",
                4.0D,
                70,
                "右肘",
                1.0D,
                260,
                24);

        Map<String, Object> enriched = assembler.enrichResult(new HashMap<>(), context);

        assertThat(enriched.get("mock")).isEqualTo(true);
        assertThat(enriched.get("analysisMode")).isEqualTo("mock-rule-based");
        assertThat(enriched.get("analysisLabel")).isEqualTo("模拟分析（规则辅助）");
    }

    @Test
    void coronaryAssemblerShouldExposeMockRuleMetadata() {
        CoronaryCtaResultAssembler assembler = new CoronaryCtaResultAssembler();
        CoronaryCtaPreparedContext context = new CoronaryCtaPreparedContext(
                "pacs",
                "F:/tmp/coronary.nii.gz",
                "李四",
                "CTA-4001",
                "男",
                61,
                LocalDate.of(2026, 3, 16),
                "Philips iCT 256",
                "coronary.nii.gz",
                68,
                4,
                "75% (Diastolic)",
                "100 kV",
                0.7D);

        Map<String, Object> enriched = assembler.enrichResult(new HashMap<>(), context);

        assertThat(enriched.get("mock")).isEqualTo(true);
        assertThat(enriched.get("analysisMode")).isEqualTo("mock-rule-based");
        assertThat(enriched.get("analysisLabel")).isEqualTo("模拟分析（规则辅助）");
    }
}
