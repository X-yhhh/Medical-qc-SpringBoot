package com.medical.qc.modules.qctask.application.support;

import com.medical.qc.support.MockQualityAnalysisSupport;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 冠脉 CTA 规则分析器。
 *
 * <p>基于采集参数和轻量体数据采样输出稳定的规则辅助判定，不依赖大模型推理。</p>
 */
@Component
public class CoronaryCtaRuleAnalyzer {
    private static final String MODEL_CODE = "coronary_cta_qc_rule_v1";
    private static final String MODEL_VERSION = "rules-2026.03";

    public Map<String, Object> analyze(CoronaryCtaPreparedContext context) {
        NiftiSampleStats sampleStats = loadSampleStats(context.analysisVolumePath());

        Map<String, Object> patientInfo = RuleAnalysisSupport.createPatientInfo(
                context.patientName(),
                context.examId(),
                context.sourceMode(),
                context.originalFilename());
        patientInfo.put("gender", context.gender());
        patientInfo.put("age", context.age());
        patientInfo.put("studyDate", context.studyDate() == null ? patientInfo.get("studyDate") : context.studyDate().toString());
        patientInfo.put("device", context.scannerModel());
        patientInfo.put("heartRate", context.heartRate());
        patientInfo.put("hrVariability", context.hrVariability());
        patientInfo.put("reconPhase", context.reconPhase());
        patientInfo.put("kVp", context.kVp());
        patientInfo.put("sliceThickness", context.sliceThickness() != null
                ? context.sliceThickness()
                : (sampleStats == null ? null : sampleStats.spacingZ()));
        if (sampleStats != null) {
            patientInfo.put("sliceCount", sampleStats.depth());
            patientInfo.put("pixelSpacing", List.of(sampleStats.spacingX(), sampleStats.spacingY()));
            patientInfo.put("volumeMeanHu", sampleStats.meanHu());
            patientInfo.put("volumeNoiseStdHu", sampleStats.stdHu());
        }

        List<Map<String, Object>> qcItems = new ArrayList<>();
        qcItems.add(buildHeartRateControlItem(context));
        qcItems.add(buildHeartRateVariabilityItem(context));
        qcItems.add(buildBreathingItem(sampleStats));
        qcItems.add(buildAortaEnhancementItem(sampleStats));
        qcItems.add(buildLadEnhancementItem(sampleStats));
        qcItems.add(buildRcaEnhancementItem(sampleStats));
        qcItems.add(buildNoiseItem(sampleStats));
        qcItems.add(buildCalcificationItem(sampleStats));
        qcItems.add(buildStepArtifactItem(sampleStats));
        qcItems.add(buildEcgGatingItem(context));
        qcItems.add(buildRangeItem(context, sampleStats));
        qcItems.add(buildMetalArtifactItem(sampleStats));

        return RuleAnalysisSupport.createResultEnvelope(
                MockQualityAnalysisSupport.TASK_TYPE_CORONARY_CTA,
                MODEL_CODE,
                MODEL_VERSION,
                patientInfo,
                qcItems,
                sampleStats == null ? 800L : 950L);
    }

    private Map<String, Object> buildHeartRateControlItem(CoronaryCtaPreparedContext context) {
        Integer heartRate = context.heartRate();
        if (heartRate == null) {
            return reviewItem("CTA_HR_CONTROL", "心率控制", "扫描期间平均心率应尽量控制在 75 bpm 以下。", "缺少平均心率信息，需人工确认采集前是否完成心率控制。");
        }
        if (heartRate <= 75) {
            return passItem("CTA_HR_CONTROL", "心率控制", "扫描期间平均心率应尽量控制在 75 bpm 以下。", "平均心率 " + heartRate + " bpm，满足规则版重建要求。");
        }
        return failItem("CTA_HR_CONTROL", "心率控制", "扫描期间平均心率应尽量控制在 75 bpm 以下。", "平均心率 " + heartRate + " bpm，高于推荐范围，易影响冠脉重建质量。");
    }

    private Map<String, Object> buildHeartRateVariabilityItem(CoronaryCtaPreparedContext context) {
        Integer variability = context.hrVariability();
        if (variability == null) {
            return reviewItem("CTA_HR_VARIABILITY", "心率稳定性", "扫描期间心率波动应尽量控制在 5 bpm 以内。", "缺少心率波动信息，需人工确认门控稳定性。");
        }
        if (variability <= 5) {
            return passItem("CTA_HR_VARIABILITY", "心率稳定性", "扫描期间心率波动应尽量控制在 5 bpm 以内。", "心率波动 " + variability + " bpm，满足门控稳定性要求。");
        }
        return failItem("CTA_HR_VARIABILITY", "心率稳定性", "扫描期间心率波动应尽量控制在 5 bpm 以内。", "心率波动 " + variability + " bpm，高于推荐范围，易出现层间错位。");
    }

    private Map<String, Object> buildBreathingItem(NiftiSampleStats sampleStats) {
        if (sampleStats == null) {
            return reviewItem("CTA_BREATHING", "呼吸配合", "检查过程中应避免明显呼吸运动伪影。", "缺少可采样的冠脉体数据，需人工确认屏气配合。");
        }
        if (sampleStats.meanAbsoluteSliceShift() <= 8.0D) {
            return passItem("CTA_BREATHING", "呼吸配合", "检查过程中应避免明显呼吸运动伪影。", "层间均值位移 " + sampleStats.meanAbsoluteSliceShift() + " HU，呼吸稳定性良好。");
        }
        if (sampleStats.meanAbsoluteSliceShift() <= 18.0D) {
            return reviewItem("CTA_BREATHING", "呼吸配合", "检查过程中应避免明显呼吸运动伪影。", "层间均值位移 " + sampleStats.meanAbsoluteSliceShift() + " HU，建议人工复核膈肌附近层面。");
        }
        return failItem("CTA_BREATHING", "呼吸配合", "检查过程中应避免明显呼吸运动伪影。", "层间均值位移 " + sampleStats.meanAbsoluteSliceShift() + " HU，提示存在明显呼吸运动风险。");
    }

    private Map<String, Object> buildAortaEnhancementItem(NiftiSampleStats sampleStats) {
        if (sampleStats == null) {
            return reviewItem("CTA_AO_ENHANCEMENT", "血管强化 (AO)", "基于降采样体数据粗评估主动脉强化水平。", "缺少体数据采样结果，无法粗评估主动脉强化。");
        }
        if (sampleStats.maxHu() >= 300.0D || sampleStats.highDensityRatio() >= 0.008D) {
            return passItem("CTA_AO_ENHANCEMENT", "血管强化 (AO)", "基于降采样体数据粗评估主动脉强化水平。", "高密度分布充分（max " + sampleStats.maxHu() + " HU），主动脉强化粗评估达标。");
        }
        if (sampleStats.maxHu() >= 220.0D || sampleStats.highDensityRatio() >= 0.004D) {
            return reviewItem("CTA_AO_ENHANCEMENT", "血管强化 (AO)", "基于降采样体数据粗评估主动脉强化水平。", "高密度分布处于边界范围，建议人工复核主动脉强化效果。");
        }
        return failItem("CTA_AO_ENHANCEMENT", "血管强化 (AO)", "基于降采样体数据粗评估主动脉强化水平。", "高密度体素比例偏低，主动脉强化粗评估不足。");
    }

    private Map<String, Object> buildLadEnhancementItem(NiftiSampleStats sampleStats) {
        if (sampleStats == null) {
            return reviewItem("CTA_LAD_ENHANCEMENT", "血管强化 (LAD)", "基于降采样体数据粗评估左前降支强化水平。", "缺少体数据采样结果，无法粗评估左前降支强化。");
        }
        if (sampleStats.maxHu() >= 260.0D && sampleStats.highDensityRatio() >= 0.004D) {
            return passItem("CTA_LAD_ENHANCEMENT", "血管强化 (LAD)", "基于降采样体数据粗评估左前降支强化水平。", "高密度分布满足粗评估阈值，左前降支强化基本达标。");
        }
        if (sampleStats.maxHu() >= 220.0D && sampleStats.highDensityRatio() >= 0.002D) {
            return reviewItem("CTA_LAD_ENHANCEMENT", "血管强化 (LAD)", "基于降采样体数据粗评估左前降支强化水平。", "左前降支强化处于边界范围，建议结合原始层面人工确认。");
        }
        return failItem("CTA_LAD_ENHANCEMENT", "血管强化 (LAD)", "基于降采样体数据粗评估左前降支强化水平。", "高密度特征偏弱，左前降支强化粗评估不足。");
    }

    private Map<String, Object> buildRcaEnhancementItem(NiftiSampleStats sampleStats) {
        if (sampleStats == null) {
            return reviewItem("CTA_RCA_ENHANCEMENT", "血管强化 (RCA)", "基于降采样体数据粗评估右冠状动脉强化水平。", "缺少体数据采样结果，无法粗评估右冠强化。");
        }
        if (sampleStats.maxHu() >= 250.0D && sampleStats.highDensityRatio() >= 0.003D) {
            return passItem("CTA_RCA_ENHANCEMENT", "血管强化 (RCA)", "基于降采样体数据粗评估右冠状动脉强化水平。", "高密度体素比例满足粗评估阈值，右冠强化基本达标。");
        }
        if (sampleStats.maxHu() >= 210.0D && sampleStats.highDensityRatio() >= 0.0015D) {
            return reviewItem("CTA_RCA_ENHANCEMENT", "血管强化 (RCA)", "基于降采样体数据粗评估右冠状动脉强化水平。", "右冠强化处于边界范围，建议人工复核近端与远端层面。");
        }
        return failItem("CTA_RCA_ENHANCEMENT", "血管强化 (RCA)", "基于降采样体数据粗评估右冠状动脉强化水平。", "高密度特征偏弱，右冠强化粗评估不足。");
    }

    private Map<String, Object> buildNoiseItem(NiftiSampleStats sampleStats) {
        if (sampleStats == null) {
            return reviewItem("CTA_NOISE", "噪声水平", "降采样体数据标准差用于粗评估整体噪声水平。", "缺少体数据采样结果，无法估计噪声水平。");
        }
        if (sampleStats.stdHu() <= 160.0D) {
            return passItem("CTA_NOISE", "噪声水平", "降采样体数据标准差用于粗评估整体噪声水平。", "标准差 " + sampleStats.stdHu() + " HU，噪声处于可接受范围。");
        }
        if (sampleStats.stdHu() <= 220.0D) {
            return reviewItem("CTA_NOISE", "噪声水平", "降采样体数据标准差用于粗评估整体噪声水平。", "标准差 " + sampleStats.stdHu() + " HU，建议人工关注远端血管显示。");
        }
        return failItem("CTA_NOISE", "噪声水平", "降采样体数据标准差用于粗评估整体噪声水平。", "标准差 " + sampleStats.stdHu() + " HU，整体噪声偏高。");
    }

    private Map<String, Object> buildCalcificationItem(NiftiSampleStats sampleStats) {
        if (sampleStats == null) {
            return reviewItem("CTA_CALCIFICATION", "钙化积分影响", "高密度峰值用于粗评估重钙化对管腔显示的影响。", "缺少体数据采样结果，无法估计重钙化影响。");
        }
        if (sampleStats.maxHu() >= 1000.0D && sampleStats.highDensityRatio() >= 0.01D) {
            return failItem("CTA_CALCIFICATION", "钙化积分影响", "高密度峰值用于粗评估重钙化对管腔显示的影响。", "高密度峰值 " + sampleStats.maxHu() + " HU，提示重钙化可能明显影响管腔评估。");
        }
        if (sampleStats.maxHu() >= 800.0D) {
            return reviewItem("CTA_CALCIFICATION", "钙化积分影响", "高密度峰值用于粗评估重钙化对管腔显示的影响。", "存在较高密度峰值，建议人工确认钙化斑块对管腔评估的影响。");
        }
        return passItem("CTA_CALCIFICATION", "钙化积分影响", "高密度峰值用于粗评估重钙化对管腔显示的影响。", "未见明显重钙化高风险特征。");
    }

    private Map<String, Object> buildStepArtifactItem(NiftiSampleStats sampleStats) {
        if (sampleStats == null) {
            return reviewItem("CTA_STEP_ARTIFACT", "台阶伪影", "层间均值位移用于粗评估台阶伪影风险。", "缺少体数据采样结果，无法粗评估台阶伪影。");
        }
        if (sampleStats.meanAbsoluteSliceShift() <= 8.0D) {
            return passItem("CTA_STEP_ARTIFACT", "台阶伪影", "层间均值位移用于粗评估台阶伪影风险。", "层间位移平稳，未见明显台阶伪影风险。");
        }
        if (sampleStats.meanAbsoluteSliceShift() <= 18.0D) {
            return reviewItem("CTA_STEP_ARTIFACT", "台阶伪影", "层间均值位移用于粗评估台阶伪影风险。", "层间位移存在波动，建议人工复核冠脉连续性。");
        }
        return failItem("CTA_STEP_ARTIFACT", "台阶伪影", "层间均值位移用于粗评估台阶伪影风险。", "层间位移明显，存在台阶伪影高风险。");
    }

    private Map<String, Object> buildEcgGatingItem(CoronaryCtaPreparedContext context) {
        String reconPhase = RuleAnalysisSupport.normalizeText(context.reconPhase());
        Integer heartRate = context.heartRate();
        if (StringUtils.hasText(reconPhase)) {
            return passItem("CTA_ECG_GATING", "心电门控", "应提供有效的 ECG 门控或重建相位信息。", "重建相位 " + reconPhase + "，门控信息完整。");
        }
        if (heartRate != null && heartRate > 75) {
            return failItem("CTA_ECG_GATING", "心电门控", "应提供有效的 ECG 门控或重建相位信息。", "缺少重建相位信息且心率偏高，建议人工确认门控质量。");
        }
        return reviewItem("CTA_ECG_GATING", "心电门控", "应提供有效的 ECG 门控或重建相位信息。", "缺少重建相位信息，无法稳定判断门控质量。");
    }

    private Map<String, Object> buildRangeItem(CoronaryCtaPreparedContext context, NiftiSampleStats sampleStats) {
        double sliceThickness = context.sliceThickness() != null
                ? context.sliceThickness()
                : (sampleStats == null ? 0.0D : sampleStats.spacingZ());
        if (sampleStats != null && sampleStats.depth() >= 180 && sliceThickness > 0.0D && sliceThickness <= 1.2D) {
            return passItem("CTA_RANGE", "扫描范围", "应覆盖冠脉诊断所需的心脏范围。", "体数据深度 " + sampleStats.depth() + " 层、层厚 " + sliceThickness + " mm，覆盖范围基本满足要求。");
        }
        if (sampleStats != null && sampleStats.depth() >= 120) {
            return reviewItem("CTA_RANGE", "扫描范围", "应覆盖冠脉诊断所需的心脏范围。", "体数据深度 " + sampleStats.depth() + " 层，建议人工复核心脏上下缘是否完整。");
        }
        if (sliceThickness > 1.5D) {
            return failItem("CTA_RANGE", "扫描范围", "应覆盖冠脉诊断所需的心脏范围。", "层厚 " + sliceThickness + " mm 偏大，可能影响冠脉重建与范围判断。");
        }
        return reviewItem("CTA_RANGE", "扫描范围", "应覆盖冠脉诊断所需的心脏范围。", "缺少足够的体数据统计，需人工确认扫描范围。");
    }

    private Map<String, Object> buildMetalArtifactItem(NiftiSampleStats sampleStats) {
        if (sampleStats == null) {
            return reviewItem("CTA_METAL_ARTIFACT", "金属/线束伪影", "高密度峰值用于粗评估金属或线束伪影风险。", "缺少体数据采样结果，无法粗评估金属/线束伪影。");
        }
        if (sampleStats.maxHu() >= 1500.0D) {
            return failItem("CTA_METAL_ARTIFACT", "金属/线束伪影", "高密度峰值用于粗评估金属或线束伪影风险。", "峰值 " + sampleStats.maxHu() + " HU，提示存在明显金属或线束伪影风险。");
        }
        if (sampleStats.maxHu() >= 1000.0D) {
            return reviewItem("CTA_METAL_ARTIFACT", "金属/线束伪影", "高密度峰值用于粗评估金属或线束伪影风险。", "存在较高密度峰值，建议人工复核上腔静脉或电极片附近层面。");
        }
        return passItem("CTA_METAL_ARTIFACT", "金属/线束伪影", "高密度峰值用于粗评估金属或线束伪影风险。", "未见明显金属/线束伪影高风险特征。");
    }

    private NiftiSampleStats loadSampleStats(String analysisVolumePath) {
        if (!StringUtils.hasText(analysisVolumePath)) {
            return null;
        }
        Path filePath = Paths.get(analysisVolumePath);
        if (!Files.exists(filePath)) {
            return null;
        }
        try {
            return NiftiSampleStatsReader.read(filePath);
        } catch (Exception exception) {
            return null;
        }
    }

    private Map<String, Object> passItem(String itemCode, String name, String description, String detail) {
        return RuleAnalysisSupport.createQcItem(itemCode, name, RuleAnalysisSupport.STATUS_PASS, description, detail);
    }

    private Map<String, Object> failItem(String itemCode, String name, String description, String detail) {
        return RuleAnalysisSupport.createQcItem(itemCode, name, RuleAnalysisSupport.STATUS_FAIL, description, detail);
    }

    private Map<String, Object> reviewItem(String itemCode, String name, String description, String detail) {
        return RuleAnalysisSupport.createQcItem(itemCode, name, RuleAnalysisSupport.STATUS_REVIEW, description, detail);
    }
}
