package com.medical.qc.support;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 质控任务公共辅助工具测试。
 */
class MockQualityAnalysisSupportTest {

    @Test
    void shouldResolveReadableTaskTypeNames() {
        assertEquals("头部出血检测", MockQualityAnalysisSupport.resolveTaskTypeName("hemorrhage"));
        assertEquals("CT头部平扫质控", MockQualityAnalysisSupport.resolveTaskTypeName("head"));
        assertEquals("CT胸部平扫质控", MockQualityAnalysisSupport.resolveTaskTypeName("chest-non-contrast"));
    }

    @Test
    void shouldTreatReviewItemsAsAbnormal() {
        Map<String, Object> result = Map.of(
                "qcItems", List.of(
                        Map.of("name", "定位像范围", "status", "合格"),
                        Map.of("name", "纵隔窗设置", "status", "待人工确认")));

        assertEquals(1, MockQualityAnalysisSupport.resolveAbnormalCount(result));
        assertEquals("待人工确认", MockQualityAnalysisSupport.resolveQcStatus(result));
        assertEquals("纵隔窗设置", MockQualityAnalysisSupport.resolvePrimaryIssue(result));
    }

    @Test
    void shouldNormalizeSummaryWhenQcItemsAndSummaryConflict() {
        Map<String, Object> result = Map.of(
                "summary", Map.of(
                        "result", "合格",
                        "qualityScore", 86,
                        "abnormalCount", 1),
                "qcItems", List.of(
                        Map.of("name", "定位像范围", "status", "合格"),
                        Map.of("name", "静脉污染", "status", "不合格")));

        Map<String, Object> normalized = MockQualityAnalysisSupport.normalizeResultPayload(result);
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) normalized.get("summary");

        assertEquals("不合格", MockQualityAnalysisSupport.resolveQcStatus(normalized));
        assertEquals("不合格", summary.get("result"));
        assertEquals(1, summary.get("abnormalCount"));
        assertEquals(1, summary.get("failCount"));
        assertEquals(0, summary.get("reviewCount"));
        assertEquals("静脉污染", summary.get("primaryIssue"));
        assertEquals("静脉污染", normalized.get("primaryIssue"));
    }

    @Test
    void shouldKeepPrimaryIssueNullForNormalizedTopLevelWhenNoAbnormal() {
        Map<String, Object> result = Map.of(
                "qcItems", List.of(
                        Map.of("name", "扫描范围", "status", "合格"),
                        Map.of("name", "呼吸配合", "status", "合格")));

        Map<String, Object> normalized = MockQualityAnalysisSupport.normalizeResultPayload(result);
        assertEquals("合格", MockQualityAnalysisSupport.resolveQcStatus(normalized));
        assertEquals("未见明显异常", normalized.get("primaryIssue"));
    }

    @Test
    void shouldTreatEmptyResultAsReviewInsteadOfPass() {
        Map<String, Object> result = Map.of(
                "summary", Map.of(
                        "totalItems", 0,
                        "qualityScore", 100));

        Map<String, Object> normalized = MockQualityAnalysisSupport.normalizeResultPayload(result);

        assertEquals("待人工确认", MockQualityAnalysisSupport.resolveQcStatus(normalized));
        assertEquals("结果不完整", MockQualityAnalysisSupport.resolvePrimaryIssue(normalized));
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) normalized.get("summary");
        assertEquals("待人工确认", summary.get("result"));
        assertEquals("结果不完整", summary.get("primaryIssue"));
    }
}
