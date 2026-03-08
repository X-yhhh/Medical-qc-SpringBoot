package com.medical.qc.support;

import com.medical.qc.entity.HemorrhageRecord;
import org.springframework.util.StringUtils;

/**
 * 脑出血检测异常项解析工具。
 *
 * <p>统一维护首页、异常汇总页和历史记录页的主异常项、优先级与严重度口径，
 * 避免前后端在不同位置出现不一致的判断逻辑。</p>
 */
public final class HemorrhageIssueSupport {
    private static final String PRIMARY_ISSUE_NORMAL = "未见明显异常";

    private HemorrhageIssueSupport() {
    }

    /**
     * 解析脑出血记录的主异常项。
     * 优先级从高到低为：脑出血 > 中线偏移 > 脑室结构异常 > 未见明显异常。
     *
     * @param record 脑出血检测历史记录
     * @return 主异常项文案
     */
    public static String resolvePrimaryIssue(HemorrhageRecord record) {
        if (record == null) {
            return PRIMARY_ISSUE_NORMAL;
        }

        if ("出血".equals(record.getPrediction())) {
            return "脑出血";
        }

        if (Boolean.TRUE.equals(record.getMidlineShift())) {
            if (StringUtils.hasText(record.getMidlineDetail())) {
                return record.getMidlineDetail();
            }
            return "中线偏移";
        }

        if (Boolean.TRUE.equals(record.getVentricleIssue())) {
            if (StringUtils.hasText(record.getVentricleDetail())) {
                return record.getVentricleDetail();
            }
            return "脑室结构异常";
        }

        return PRIMARY_ISSUE_NORMAL;
    }

    /**
     * 判断记录是否存在异常。
     *
     * <p>优先基于实时推导的主异常项判断，避免历史数据中的 qcStatus 因旧逻辑未覆盖
     * 中线偏移/脑室结构异常而出现漏判。</p>
     *
     * @param record 脑出血检测历史记录
     * @return 是否异常
     */
    public static boolean isAbnormalRecord(HemorrhageRecord record) {
        if (record == null) {
            return false;
        }

        if ("不合格".equals(record.getQcStatus())) {
            return true;
        }

        return !PRIMARY_ISSUE_NORMAL.equals(resolvePrimaryIssue(record));
    }

    /**
     * 判断记录是否为合格结果。
     *
     * @param record 脑出血检测历史记录
     * @return 是否合格
     */
    public static boolean isQualifiedRecord(HemorrhageRecord record) {
        return record != null && !isAbnormalRecord(record);
    }

    /**
     * 根据记录内容推导统一质控结论。
     *
     * @param record 脑出血检测历史记录
     * @return 合格 / 不合格
     */
    public static String resolveQcStatus(HemorrhageRecord record) {
        return isAbnormalRecord(record) ? "不合格" : "合格";
    }

    /**
     * 根据主异常项推导页面展示优先级。
     *
     * @param primaryIssue 主异常项
     * @return 优先级：高 / 中 / 低
     */
    public static String resolvePriority(String primaryIssue) {
        if ("脑出血".equals(primaryIssue)) {
            return "高";
        }

        if (primaryIssue != null && (primaryIssue.contains("中线") || primaryIssue.contains("脑室"))) {
            return "中";
        }

        return "低";
    }

    /**
     * 将优先级映射为数值严重度，便于排序。
     *
     * @param primaryIssue 主异常项
     * @return 严重度值，数值越大越严重
     */
    public static int resolveSeverityLevel(String primaryIssue) {
        if ("脑出血".equals(primaryIssue)) {
            return 3;
        }

        if (primaryIssue != null && primaryIssue.contains("中线")) {
            return 2;
        }

        if (primaryIssue != null && primaryIssue.contains("脑室")) {
            return 1;
        }

        return 0;
    }

    /**
     * 构造异常汇总页与工单页共用的异常描述。
     *
     * @param primaryIssue 主异常项
     * @return 描述文案
     */
    public static String buildIssueDescription(String primaryIssue) {
        return "主要异常：" + (StringUtils.hasText(primaryIssue) ? primaryIssue : "未见明显异常");
    }
}
