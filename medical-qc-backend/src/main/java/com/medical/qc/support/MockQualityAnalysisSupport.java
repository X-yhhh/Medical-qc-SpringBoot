package com.medical.qc.support;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 其余四个质控模块的模拟分析支持类。
 *
 * <p>当前仍返回 mock 结果，但会直接生成前端页面可渲染的完整结构：patientInfo + qcItems。</p>
 * <p>后续接入真实算法时，只需替换各任务类型的结果构建逻辑，控制层、MQ 提交与轮询协议可保持不变。</p>
 */
public final class MockQualityAnalysisSupport {
    public static final String TASK_TYPE_HEAD = "head";
    public static final String TASK_TYPE_CHEST_NON_CONTRAST = "chest-non-contrast";
    public static final String TASK_TYPE_CHEST_CONTRAST = "chest-contrast";
    public static final String TASK_TYPE_CORONARY_CTA = "coronary-cta";

    public static final String SOURCE_MODE_LOCAL = "local";
    public static final String SOURCE_MODE_PACS = "pacs";

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private MockQualityAnalysisSupport() {
    }

    public static boolean isSupportedTaskType(String taskType) {
        return TASK_TYPE_HEAD.equals(taskType)
                || TASK_TYPE_CHEST_NON_CONTRAST.equals(taskType)
                || TASK_TYPE_CHEST_CONTRAST.equals(taskType)
                || TASK_TYPE_CORONARY_CTA.equals(taskType);
    }

    public static boolean isSupportedSourceMode(String sourceMode) {
        return SOURCE_MODE_LOCAL.equals(sourceMode) || SOURCE_MODE_PACS.equals(sourceMode);
    }

    public static String normalizeSourceMode(String sourceMode) {
        if (sourceMode == null || sourceMode.isBlank()) {
            return SOURCE_MODE_LOCAL;
        }

        String normalized = sourceMode.trim().toLowerCase();
        if (SOURCE_MODE_PACS.equals(normalized)) {
            return SOURCE_MODE_PACS;
        }

        if (SOURCE_MODE_LOCAL.equals(normalized)) {
            return SOURCE_MODE_LOCAL;
        }

        return normalized;
    }

    public static String resolveTaskTypeName(String taskType) {
        if (TASK_TYPE_HEAD.equals(taskType)) {
            return "CT头部平扫质控";
        }
        if (TASK_TYPE_CHEST_NON_CONTRAST.equals(taskType)) {
            return "CT胸部平扫质控";
        }
        if (TASK_TYPE_CHEST_CONTRAST.equals(taskType)) {
            return "CT胸部增强质控";
        }
        if (TASK_TYPE_CORONARY_CTA.equals(taskType)) {
            return "冠脉CTA质控";
        }
        return taskType;
    }

    /**
     * 提取结果中的患者信息对象。
     *
     * @param result 质控结果
     * @return 患者信息映射；缺失时返回空映射
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> extractPatientInfo(Map<String, Object> result) {
        if (result == null) {
            return Collections.emptyMap();
        }

        Object patientInfo = result.get("patientInfo");
        if (patientInfo instanceof Map<?, ?> patientInfoMap) {
            return (Map<String, Object>) patientInfoMap;
        }

        return Collections.emptyMap();
    }

    /**
     * 提取结果中的摘要对象。
     *
     * @param result 质控结果
     * @return 摘要映射；缺失时返回空映射
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> extractSummary(Map<String, Object> result) {
        if (result == null) {
            return Collections.emptyMap();
        }

        Object summary = result.get("summary");
        if (summary instanceof Map<?, ?> summaryMap) {
            return (Map<String, Object>) summaryMap;
        }

        return Collections.emptyMap();
    }

    /**
     * 提取结果中的质控项列表。
     *
     * @param result 质控结果
     * @return 质控项列表；缺失时返回空列表
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> extractQcItems(Map<String, Object> result) {
        if (result == null) {
            return List.of();
        }

        Object qcItems = result.get("qcItems");
        if (qcItems instanceof List<?> qcItemList) {
            return (List<Map<String, Object>>) qcItemList;
        }

        return List.of();
    }

    /**
     * 从质控结果中解析异常项数量。
     *
     * @param result 质控结果
     * @return 异常项数量
     */
    public static int resolveAbnormalCount(Map<String, Object> result) {
        Object abnormalCount = extractSummary(result).get("abnormalCount");
        if (abnormalCount instanceof Number number) {
            return Math.max(number.intValue(), 0);
        }

        return (int) extractQcItems(result).stream()
                .filter(item -> "不合格".equals(item.get("status")))
                .count();
    }

    /**
     * 从质控结果中解析质控评分。
     *
     * @param result 质控结果
     * @return 质控评分
     */
    public static double resolveQualityScore(Map<String, Object> result) {
        Object qualityScore = extractSummary(result).get("qualityScore");
        if (qualityScore instanceof Number number) {
            return number.doubleValue();
        }

        int totalItems = extractQcItems(result).size();
        if (totalItems == 0) {
            return 0D;
        }

        return Math.round((totalItems - resolveAbnormalCount(result)) * 1000.0D / totalItems) / 10.0D;
    }

    /**
     * 从质控结果中解析质控结论。
     *
     * @param result 质控结果
     * @return 合格/不合格
     */
    public static String resolveQcStatus(Map<String, Object> result) {
        Object qcStatus = extractSummary(result).get("result");
        if (qcStatus instanceof String qcStatusText && !qcStatusText.isBlank()) {
            return qcStatusText.trim();
        }

        return resolveAbnormalCount(result) > 0 ? "不合格" : "合格";
    }

    /**
     * 从质控结果中解析主异常项。
     *
     * @param result 质控结果
     * @return 主异常项；若未发现异常则返回“未见明显异常”
     */
    public static String resolvePrimaryIssue(Map<String, Object> result) {
        for (Map<String, Object> item : extractQcItems(result)) {
            if ("不合格".equals(item.get("status"))) {
                Object issueName = item.get("name");
                if (issueName instanceof String issueText && !issueText.isBlank()) {
                    return issueText.trim();
                }
            }
        }

        return "未见明显异常";
    }

    /**
     * 构建前端页面可直接使用的完整 mock 结果。
     */
    public static Map<String, Object> createMockResult(String taskType,
                                                       String patientName,
                                                       String examId,
                                                       String sourceMode,
                                                       String originalFilename) {
        String normalizedSourceMode = normalizeSourceMode(sourceMode);
        if (TASK_TYPE_HEAD.equals(taskType)) {
            return createHeadResult(patientName, examId, normalizedSourceMode, originalFilename);
        }
        if (TASK_TYPE_CHEST_NON_CONTRAST.equals(taskType)) {
            return createChestNonContrastResult(patientName, examId, normalizedSourceMode, originalFilename);
        }
        if (TASK_TYPE_CHEST_CONTRAST.equals(taskType)) {
            return createChestContrastResult(patientName, examId, normalizedSourceMode, originalFilename);
        }
        if (TASK_TYPE_CORONARY_CTA.equals(taskType)) {
            return createCoronaryCtaResult(patientName, examId, normalizedSourceMode, originalFilename);
        }

        throw new IllegalArgumentException("不支持的质控任务类型: " + taskType);
    }

    /**
     * 保留一个简版入口，兼容当前仍存在的旧 mock 调用点。
     */
    public static Map<String, Object> createMockResult(String taskType) {
        return createMockResult(taskType, "匿名患者", "MOCK-EXAM", SOURCE_MODE_LOCAL, "mock-image.dcm");
    }

    private static Map<String, Object> createHeadResult(String patientName,
                                                        String examId,
                                                        String sourceMode,
                                                        String originalFilename) {
        List<Map<String, Object>> qcItems = new ArrayList<>();
        qcItems.add(createQcItem("扫描覆盖范围", "合格", "扫描范围应覆盖从颅底至颅顶完整区域", ""));
        qcItems.add(createQcItem("体位不正", randomStatus(0.15D), "正中矢状面应与扫描架中心线重合", "头部存在轻度偏斜，建议重新摆位"));
        qcItems.add(createQcItem("运动伪影", randomStatus(0.25D), "图像中不应出现因患者运动导致的模糊或重影", "检测到明显层间错位，建议固定头部后重新扫描"));
        qcItems.add(createQcItem("金属伪影", randomStatus(0.1D), "应避免假牙、发卡等金属异物干扰", "颅底区域存在放射状金属伪影，影响后颅窝观察"));
        qcItems.add(createQcItem("层厚层间距", "合格", "常规扫描层厚应≤5mm", ""));
        qcItems.add(createQcItem("剂量控制 (CTDI)", "合格", "CTDIvol 应低于参考水平 (60 mGy)", ""));

        Map<String, Object> patientInfo = createBasePatientInfo(patientName, examId, sourceMode, originalFilename);
        patientInfo.put("device", "GE Revolution CT");
        patientInfo.put("sliceCount", 240);
        patientInfo.put("sliceThickness", 5.0);

        return createResultEnvelope(TASK_TYPE_HEAD, patientInfo, qcItems, 1100);
    }

    private static Map<String, Object> createChestNonContrastResult(String patientName,
                                                                    String examId,
                                                                    String sourceMode,
                                                                    String originalFilename) {
        List<Map<String, Object>> qcItems = new ArrayList<>();
        qcItems.add(createQcItem("扫描范围", "合格", "肺尖至肺底完整覆盖", ""));
        qcItems.add(createQcItem("呼吸伪影", randomStatus(0.2D), "无明显呼吸运动伪影", "屏气配合一般，双下肺局部轻度模糊"));
        qcItems.add(createQcItem("体位不正", randomStatus(0.15D), "患者居中，无倾斜", "胸廓轻度偏转，建议复扫时重新居中"));
        qcItems.add(createQcItem("金属伪影", randomStatus(0.18D), "无明显金属伪影干扰", "左侧胸壁可见少量金属伪影"));
        qcItems.add(createQcItem("图像噪声", "合格", "噪声指数符合诊断要求", ""));
        qcItems.add(createQcItem("肺窗设置", "合格", "窗宽窗位适宜观察肺纹理", ""));
        qcItems.add(createQcItem("纵隔窗设置", "合格", "窗宽窗位适宜观察纵隔结构", ""));
        qcItems.add(createQcItem("心影干扰", randomStatus(0.12D), "心脏搏动伪影在可接受范围内", "心影边缘轻度拖影，建议优化屏气配合"));

        Map<String, Object> patientInfo = createBasePatientInfo(patientName, examId, sourceMode, originalFilename);
        patientInfo.put("device", "GE Revolution CT");
        patientInfo.put("sliceCount", 300);
        patientInfo.put("sliceThickness", 1.25);

        return createResultEnvelope(TASK_TYPE_CHEST_NON_CONTRAST, patientInfo, qcItems, 1300);
    }

    private static Map<String, Object> createChestContrastResult(String patientName,
                                                                 String examId,
                                                                 String sourceMode,
                                                                 String originalFilename) {
        List<Map<String, Object>> qcItems = new ArrayList<>();
        qcItems.add(createQcItem("定位像范围", "合格", "包含肺尖至肺底完整范围", "", "定位片"));
        qcItems.add(createQcItem("呼吸配合", randomStatus(0.18D), "无明显呼吸运动伪影", "双下肺局部呼吸伪影稍明显", "平扫期"));
        qcItems.add(createQcItem("金属伪影", "合格", "无明显金属植入物伪影", "", "平扫期"));
        qcItems.add(createQcItem("主动脉强化值", "合格", "主动脉弓CT值应 > 250 HU", "实测平均值 320 HU", "增强I期"));
        qcItems.add(createQcItem("肺动脉强化", "合格", "肺动脉主干CT值应 > 200 HU", "实测平均值 280 HU", "增强I期"));
        qcItems.add(createQcItem("静脉污染", randomStatus(0.25D), "上腔静脉无明显高密度伪影", "上腔静脉见明显条束状硬化伪影，建议生理盐水冲刷", "增强I期"));
        qcItems.add(createQcItem("实质强化均匀度", "合格", "肝脏实质强化均匀", "", "增强II期"));

        Map<String, Object> patientInfo = createBasePatientInfo(patientName, examId, sourceMode, originalFilename);
        patientInfo.put("device", "Siemens Somatom Force");
        patientInfo.put("sliceCount", 320);
        patientInfo.put("sliceThickness", 1.0);
        patientInfo.put("flowRate", 4.5);
        patientInfo.put("contrastVolume", 80);
        patientInfo.put("injectionSite", "右侧肘正中静脉");

        return createResultEnvelope(TASK_TYPE_CHEST_CONTRAST, patientInfo, qcItems, 1450);
    }

    private static Map<String, Object> createCoronaryCtaResult(String patientName,
                                                               String examId,
                                                               String sourceMode,
                                                               String originalFilename) {
        List<Map<String, Object>> qcItems = new ArrayList<>();
        qcItems.add(createQcItem("心率控制", "合格", "检查扫描期间平均心率是否符合重建要求", "平均心率 62 bpm (<=75 bpm)"));
        qcItems.add(createQcItem("心率稳定性", "合格", "检查扫描期间心率波动情况", "心率波动 < 5 bpm"));
        qcItems.add(createQcItem("呼吸配合", randomStatus(0.16D), "检查是否存在呼吸运动伪影", "膈肌位置略有漂移，建议加强屏气训练"));
        qcItems.add(createQcItem("血管强化 (AO)", "合格", "升主动脉根部 CT 值", "CT值 380 HU (>=300 HU)"));
        qcItems.add(createQcItem("血管强化 (LAD)", "合格", "左前降支远端 CT 值", "CT值 280 HU (>=250 HU)"));
        qcItems.add(createQcItem("血管强化 (RCA)", randomStatus(0.22D), "右冠状动脉远端 CT 值", "CT值 210 HU (<250 HU)，强化稍显不足"));
        qcItems.add(createQcItem("噪声水平", "合格", "主动脉根部图像噪声 (SD)", "SD = 22 HU (<=30 HU)"));
        qcItems.add(createQcItem("钙化积分影响", randomStatus(0.12D), "严重钙化斑块导致的伪影干扰", "近端钙化较重，局部影响血管腔评估"));
        qcItems.add(createQcItem("台阶伪影", "合格", "因心率不齐或屏气不佳导致的层面错位", "血管连续性良好"));
        qcItems.add(createQcItem("心电门控", "合格", "ECG 信号同步状态", "R波触发准确"));
        qcItems.add(createQcItem("扫描范围", "合格", "覆盖气管分叉至心脏膈面下", "心脏包络完整"));
        qcItems.add(createQcItem("金属/线束伪影", randomStatus(0.2D), "上腔静脉高浓度对比剂或电极片伪影", "上腔静脉硬化伪影干扰 RCA 近段观察"));

        Map<String, Object> patientInfo = createBasePatientInfo(patientName, examId, sourceMode, originalFilename);
        patientInfo.put("device", "Philips iCT 256");
        patientInfo.put("sliceThickness", 0.6);
        patientInfo.put("heartRate", 60 + new Random().nextInt(20));
        patientInfo.put("hrVariability", new Random().nextInt(4));
        patientInfo.put("reconPhase", "75% (Diastolic)");
        patientInfo.put("kVp", "100 kV");

        return createResultEnvelope(TASK_TYPE_CORONARY_CTA, patientInfo, qcItems, 1650);
    }

    private static Map<String, Object> createResultEnvelope(String taskType,
                                                            Map<String, Object> patientInfo,
                                                            List<Map<String, Object>> qcItems,
                                                            int baseDuration) {
        Map<String, Object> response = new HashMap<>();
        response.put("taskType", taskType);
        response.put("taskTypeName", resolveTaskTypeName(taskType));
        response.put("mock", true);
        response.put("patientInfo", patientInfo);
        response.put("qcItems", qcItems);
        response.put("duration", baseDuration + new Random().nextInt(250));
        response.put("summary", buildSummary(qcItems));
        return response;
    }

    private static Map<String, Object> createBasePatientInfo(String patientName,
                                                             String examId,
                                                             String sourceMode,
                                                             String originalFilename) {
        Map<String, Object> patientInfo = new HashMap<>();
        patientInfo.put("name", patientName);
        patientInfo.put("gender", new Random().nextBoolean() ? "男" : "女");
        patientInfo.put("age", 40 + new Random().nextInt(35));
        patientInfo.put("studyId", examId);
        patientInfo.put("accessionNumber", buildAccessionNumber(sourceMode));
        patientInfo.put("studyDate", LocalDateTime.now().format(DATE_TIME_FORMATTER));
        patientInfo.put("sourceMode", sourceMode);
        patientInfo.put("sourceLabel", SOURCE_MODE_PACS.equals(sourceMode) ? "PACS 调取" : "本地上传");
        patientInfo.put("originalFilename", originalFilename);
        return patientInfo;
    }

    private static Map<String, Object> createQcItem(String name,
                                                    String status,
                                                    String description,
                                                    String detail) {
        Map<String, Object> item = new HashMap<>();
        item.put("name", name);
        item.put("status", status);
        item.put("description", description);
        item.put("detail", "合格".equals(status) ? "" : detail);
        return item;
    }

    private static Map<String, Object> createQcItem(String name,
                                                    String status,
                                                    String description,
                                                    String detail,
                                                    String phase) {
        Map<String, Object> item = createQcItem(name, status, description, detail);
        item.put("phase", phase);
        return item;
    }

    private static Map<String, Object> buildSummary(List<Map<String, Object>> qcItems) {
        long abnormalCount = qcItems.stream()
                .filter(item -> "不合格".equals(item.get("status")))
                .count();
        int totalCount = qcItems.size();
        int qualityScore = totalCount == 0 ? 0 : (int) Math.round((totalCount - abnormalCount) * 100.0D / totalCount);

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalItems", totalCount);
        summary.put("abnormalCount", abnormalCount);
        summary.put("qualityScore", qualityScore);
        summary.put("result", qualityScore >= 80 ? "合格" : "不合格");
        return summary;
    }

    private static String randomStatus(double abnormalProbability) {
        return new Random().nextDouble() < abnormalProbability ? "不合格" : "合格";
    }

    private static String buildAccessionNumber(String sourceMode) {
        String prefix = SOURCE_MODE_PACS.equals(sourceMode) ? "PACS" : "ACC";
        return prefix + (100000 + new Random().nextInt(900000));
    }
}
