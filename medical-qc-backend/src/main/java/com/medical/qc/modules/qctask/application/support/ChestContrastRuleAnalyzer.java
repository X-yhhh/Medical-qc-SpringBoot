package com.medical.qc.modules.qctask.application.support;

import com.medical.qc.support.MockQualityAnalysisSupport;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CT胸部增强规则分析器。
 *
 * <p>当前阶段不依赖真实深度学习模型，而是基于采集参数输出稳定、可解释的辅助判定。</p>
 */
@Component
public class ChestContrastRuleAnalyzer {
    private static final String MODEL_CODE = "chest_contrast_qc_rule_v1";
    private static final String MODEL_VERSION = "rules-2026.03";

    public Map<String, Object> analyze(ChestContrastPreparedContext context) {
        Map<String, Object> patientInfo = RuleAnalysisSupport.createPatientInfo(
                context.patientName(),
                context.examId(),
                context.sourceMode(),
                context.originalFilename());
        patientInfo.put("gender", context.gender());
        patientInfo.put("age", context.age());
        patientInfo.put("studyDate", context.studyDate() == null ? patientInfo.get("studyDate") : context.studyDate().toString());
        patientInfo.put("device", context.scannerModel());
        patientInfo.put("flowRate", context.flowRate());
        patientInfo.put("contrastVolume", context.contrastVolume());
        patientInfo.put("injectionSite", context.injectionSite());
        patientInfo.put("sliceThickness", context.sliceThickness());
        patientInfo.put("bolusTrackingHu", context.bolusTrackingHu());
        patientInfo.put("scanDelaySec", context.scanDelaySec());

        List<Map<String, Object>> qcItems = new ArrayList<>();
        qcItems.add(buildRangeItem(context));
        qcItems.add(buildBreathingItem(context));
        qcItems.add(buildMetalArtifactItem());
        qcItems.add(buildAortaEnhancementItem(context));
        qcItems.add(buildPulmonaryEnhancementItem(context));
        qcItems.add(buildVenousContaminationItem(context));
        qcItems.add(buildParenchymaUniformityItem(context));

        return RuleAnalysisSupport.createResultEnvelope(
                MockQualityAnalysisSupport.TASK_TYPE_CHEST_CONTRAST,
                MODEL_CODE,
                MODEL_VERSION,
                patientInfo,
                qcItems,
                650L);
    }

    private Map<String, Object> buildRangeItem(ChestContrastPreparedContext context) {
        String detail = "当前缺少定位像覆盖范围的结构化标记，需结合定位像人工确认肺尖至肺底是否完整覆盖。";
        if (context.sliceThickness() != null && context.sliceThickness() > 3.0D) {
            detail = "层厚偏大（" + context.sliceThickness() + " mm），建议结合定位像人工确认扫描覆盖范围。";
        }
        return RuleAnalysisSupport.createQcItem(
                "CHEST_CONTRAST_RANGE",
                "定位像范围",
                RuleAnalysisSupport.STATUS_REVIEW,
                "定位像应覆盖肺尖至肺底完整范围。",
                detail,
                "定位片");
    }

    private Map<String, Object> buildBreathingItem(ChestContrastPreparedContext context) {
        String detail = "当前未接入呼吸伪影图像特征分析，请结合原始序列人工确认屏气质量。";
        if (context.scanDelaySec() != null && context.scanDelaySec() < 8) {
            detail = "扫描延迟较短（" + context.scanDelaySec() + " s），建议人工复核呼吸配合与采集时机。";
        }
        return RuleAnalysisSupport.createQcItem(
                "CHEST_CONTRAST_BREATHING",
                "呼吸配合",
                RuleAnalysisSupport.STATUS_REVIEW,
                "增强扫描应避免明显呼吸运动伪影。",
                detail,
                "平扫期");
    }

    private Map<String, Object> buildMetalArtifactItem() {
        return RuleAnalysisSupport.createQcItem(
                "CHEST_CONTRAST_METAL",
                "金属伪影",
                RuleAnalysisSupport.STATUS_REVIEW,
                "应避免金属植入物或外来异物对增强序列产生明显伪影。",
                "当前未接入金属伪影图像检测，请人工确认关键层面是否受伪影干扰。",
                "平扫期");
    }

    private Map<String, Object> buildAortaEnhancementItem(ChestContrastPreparedContext context) {
        Double flowRate = context.flowRate();
        Integer contrastVolume = context.contrastVolume();
        Integer bolusTrackingHu = context.bolusTrackingHu();
        Integer scanDelaySec = context.scanDelaySec();

        if (bolusTrackingHu != null) {
            if (bolusTrackingHu >= 250) {
                return passPhaseItem("CHEST_CONTRAST_AORTA_ENHANCEMENT", "主动脉强化值", "主动脉弓强化应达到诊断要求。", "Bolus tracking 阈值 " + bolusTrackingHu + " HU，达到增强扫描推荐门限。", "增强I期");
            }
            if (bolusTrackingHu >= 200) {
                return reviewPhaseItem("CHEST_CONTRAST_AORTA_ENHANCEMENT", "主动脉强化值", "主动脉弓强化应达到诊断要求。", "Bolus tracking 阈值 " + bolusTrackingHu + " HU，接近门限，建议人工复核主动脉强化程度。", "增强I期");
            }
            return failPhaseItem("CHEST_CONTRAST_AORTA_ENHANCEMENT", "主动脉强化值", "主动脉弓强化应达到诊断要求。", "Bolus tracking 阈值仅 " + bolusTrackingHu + " HU，提示主动脉强化可能不足。", "增强I期");
        }

        if (flowRate == null || contrastVolume == null) {
            return reviewPhaseItem("CHEST_CONTRAST_AORTA_ENHANCEMENT", "主动脉强化值", "主动脉弓强化应达到诊断要求。", "缺少流速或总量参数，无法仅凭协议信息稳定判断主动脉强化是否达标。", "增强I期");
        }

        if (flowRate >= 4.0D && contrastVolume >= 70 && withinRange(scanDelaySec, 18, 35)) {
            return passPhaseItem("CHEST_CONTRAST_AORTA_ENHANCEMENT", "主动脉强化值", "主动脉弓强化应达到诊断要求。", "流速 " + flowRate + " mL/s、总量 " + contrastVolume + " mL，协议满足主动脉强化的推荐范围。", "增强I期");
        }

        if (flowRate < 3.0D || contrastVolume < 50) {
            return failPhaseItem("CHEST_CONTRAST_AORTA_ENHANCEMENT", "主动脉强化值", "主动脉弓强化应达到诊断要求。", "流速/总量偏低（" + flowRate + " mL/s, " + contrastVolume + " mL），主动脉强化存在不足风险。", "增强I期");
        }

        return reviewPhaseItem("CHEST_CONTRAST_AORTA_ENHANCEMENT", "主动脉强化值", "主动脉弓强化应达到诊断要求。", "协议参数接近临界范围，建议结合增强期图像人工确认主动脉强化效果。", "增强I期");
    }

    private Map<String, Object> buildPulmonaryEnhancementItem(ChestContrastPreparedContext context) {
        Double flowRate = context.flowRate();
        Integer contrastVolume = context.contrastVolume();
        Integer bolusTrackingHu = context.bolusTrackingHu();
        Integer scanDelaySec = context.scanDelaySec();
        if (flowRate == null || contrastVolume == null) {
            return reviewPhaseItem("CHEST_CONTRAST_PULMONARY_ENHANCEMENT", "肺动脉强化", "肺动脉主干强化应达到诊断要求。", "缺少流速或总量参数，需人工确认肺动脉强化是否充分。", "增强I期");
        }
        if (flowRate >= 3.5D && contrastVolume >= 60 && withinRange(scanDelaySec, 12, 28)) {
            String detail = "流速 " + flowRate + " mL/s、总量 " + contrastVolume + " mL";
            if (bolusTrackingHu != null) {
                detail += "，bolus tracking " + bolusTrackingHu + " HU";
            }
            detail += "，协议满足肺动脉强化的推荐区间。";
            return passPhaseItem("CHEST_CONTRAST_PULMONARY_ENHANCEMENT", "肺动脉强化", "肺动脉主干强化应达到诊断要求。", detail, "增强I期");
        }
        if (flowRate < 3.0D || contrastVolume < 50) {
            return failPhaseItem("CHEST_CONTRAST_PULMONARY_ENHANCEMENT", "肺动脉强化", "肺动脉主干强化应达到诊断要求。", "流速或总量明显不足，肺动脉强化存在不足风险。", "增强I期");
        }
        return reviewPhaseItem("CHEST_CONTRAST_PULMONARY_ENHANCEMENT", "肺动脉强化", "肺动脉主干强化应达到诊断要求。", "协议参数处于边界范围，建议人工复核肺动脉主干强化。", "增强I期");
    }

    private Map<String, Object> buildVenousContaminationItem(ChestContrastPreparedContext context) {
        String injectionSite = RuleAnalysisSupport.normalizeText(context.injectionSite());
        Integer scanDelaySec = context.scanDelaySec();
        if (injectionSite == null) {
            return reviewPhaseItem("CHEST_CONTRAST_VENOUS_CONTAMINATION", "静脉污染", "应尽量降低上腔静脉或注射静脉高浓度对比剂污染。", "缺少注射部位信息，无法稳定判断静脉污染风险。", "增强I期");
        }
        if (containsAny(injectionSite, "手背", "足背") || (scanDelaySec != null && scanDelaySec < 10)) {
            return failPhaseItem("CHEST_CONTRAST_VENOUS_CONTAMINATION", "静脉污染", "应尽量降低上腔静脉或注射静脉高浓度对比剂污染。", "注射部位或扫描延迟提示静脉污染风险偏高（" + injectionSite + "）。", "增强I期");
        }
        if (containsAny(injectionSite, "肘", "前臂") && (scanDelaySec == null || scanDelaySec >= 18)) {
            return passPhaseItem("CHEST_CONTRAST_VENOUS_CONTAMINATION", "静脉污染", "应尽量降低上腔静脉或注射静脉高浓度对比剂污染。", "注射部位为" + injectionSite + "，未见明显静脉污染高风险协议特征。", "增强I期");
        }
        return reviewPhaseItem("CHEST_CONTRAST_VENOUS_CONTAMINATION", "静脉污染", "应尽量降低上腔静脉或注射静脉高浓度对比剂污染。", "当前协议存在一定静脉污染风险，建议人工复核上腔静脉附近层面。", "增强I期");
    }

    private Map<String, Object> buildParenchymaUniformityItem(ChestContrastPreparedContext context) {
        Integer contrastVolume = context.contrastVolume();
        Integer scanDelaySec = context.scanDelaySec();
        if (contrastVolume == null || scanDelaySec == null) {
            return reviewPhaseItem("CHEST_CONTRAST_PARENCHYMA_UNIFORMITY", "实质强化均匀度", "延迟期应保证实质强化分布较均匀。", "缺少总量或扫描延迟参数，无法稳定判断实质强化均匀度。", "增强II期");
        }
        if (contrastVolume >= 70 && withinRange(scanDelaySec, 45, 75)) {
            return passPhaseItem("CHEST_CONTRAST_PARENCHYMA_UNIFORMITY", "实质强化均匀度", "延迟期应保证实质强化分布较均匀。", "总量 " + contrastVolume + " mL，延迟 " + scanDelaySec + " s，符合延迟期均匀强化的推荐范围。", "增强II期");
        }
        if (scanDelaySec < 30 || contrastVolume < 50) {
            return failPhaseItem("CHEST_CONTRAST_PARENCHYMA_UNIFORMITY", "实质强化均匀度", "延迟期应保证实质强化分布较均匀。", "延迟时间或总量不足，实质强化均匀度存在不足风险。", "增强II期");
        }
        return reviewPhaseItem("CHEST_CONTRAST_PARENCHYMA_UNIFORMITY", "实质强化均匀度", "延迟期应保证实质强化分布较均匀。", "当前协议处于边界区间，建议人工复核延迟期强化均匀度。", "增强II期");
    }

    private boolean withinRange(Integer value, int minInclusive, int maxInclusive) {
        return value != null && value >= minInclusive && value <= maxInclusive;
    }

    private boolean containsAny(String source, String... candidates) {
        if (source == null || candidates == null) {
            return false;
        }
        for (String candidate : candidates) {
            if (candidate != null && source.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> passPhaseItem(String itemCode, String name, String description, String detail, String phase) {
        return RuleAnalysisSupport.createQcItem(itemCode, name, RuleAnalysisSupport.STATUS_PASS, description, detail, phase);
    }

    private Map<String, Object> failPhaseItem(String itemCode, String name, String description, String detail, String phase) {
        return RuleAnalysisSupport.createQcItem(itemCode, name, RuleAnalysisSupport.STATUS_FAIL, description, detail, phase);
    }

    private Map<String, Object> reviewPhaseItem(String itemCode, String name, String description, String detail, String phase) {
        return RuleAnalysisSupport.createQcItem(itemCode, name, RuleAnalysisSupport.STATUS_REVIEW, description, detail, phase);
    }
}
