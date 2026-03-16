package com.medical.qc.modules.qctask.application.support;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChestContrastRuleAnalyzerTest {
    private final ChestContrastRuleAnalyzer analyzer = new ChestContrastRuleAnalyzer();

    @Test
    void shouldReturnDeterministicQcItemsForSameInput() {
        ChestContrastPreparedContext context = new ChestContrastPreparedContext(
                "local",
                "F:/tmp/chest-contrast-sample.nii.gz",
                "张三",
                "EXAM-1001",
                "男",
                52,
                LocalDate.of(2026, 3, 15),
                "Siemens Somatom Force",
                "sample.nii.gz",
                4.5D,
                80,
                "右侧肘正中静脉",
                1.0D,
                260,
                22);

        Map<String, Object> first = analyzer.analyze(context);
        Map<String, Object> second = analyzer.analyze(context);

        assertEquals(first.get("qcItems"), second.get("qcItems"));
        assertEquals(first.get("summary"), second.get("summary"));
        assertEquals("chest_contrast_qc_rule_v1", first.get("modelCode"));
        assertEquals(false, first.get("mock"));
    }

    @Test
    void shouldMarkEnhancementItemsForReviewWhenKeyMetadataMissing() {
        ChestContrastPreparedContext context = new ChestContrastPreparedContext(
                "local",
                null,
                "李四",
                "EXAM-1002",
                "女",
                46,
                LocalDate.of(2026, 3, 15),
                "GE Revolution CT",
                "sample.dcm",
                null,
                null,
                null,
                null,
                null,
                null);

        Map<String, Object> result = analyzer.analyze(context);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> qcItems = (List<Map<String, Object>>) result.get("qcItems");
        Map<String, Object> aortaItem = qcItems.stream()
                .filter(item -> "主动脉强化值".equals(item.get("name")))
                .findFirst()
                .orElseThrow();

        assertEquals("待人工确认", aortaItem.get("status"));
        assertEquals("待人工确认", ((Map<?, ?>) result.get("summary")).get("result"));
    }
}
