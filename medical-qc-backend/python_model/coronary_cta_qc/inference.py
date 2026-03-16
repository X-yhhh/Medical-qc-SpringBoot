"""
冠脉 CTA 质控的影像驱动分析模块。

当前实现复用了原有规则思路，但把输入统一收口到 Python 影像入口，
从而支持 DICOM / NIfTI / ZIP。
"""

from __future__ import annotations

from pathlib import Path

from medical_volume_utils import build_summary, read_nifti_stats


def run_inference(input_path: Path, metadata: dict[str, object] | None = None) -> dict[str, object]:
    """对单个冠脉 CTA 检查执行影像驱动分析。"""
    metadata = metadata or {}
    stats = read_nifti_stats(input_path)
    patient_info = {
        "heartRate": parse_int(metadata.get("heart_rate")),
        "hrVariability": parse_int(metadata.get("hr_variability")),
        "reconPhase": normalize_text(metadata.get("recon_phase")),
        "kVp": normalize_text(metadata.get("kvp")),
        "sliceThickness": first_non_null(parse_float(metadata.get("slice_thickness")), stats["spacing_z"]),
        "sliceCount": stats["depth"],
        "pixelSpacing": [stats["spacing_x"], stats["spacing_y"]],
        "volumeMeanHu": stats["mean_hu"],
        "volumeNoiseStdHu": stats["std_hu"],
    }

    qc_items = [
        build_heart_rate_item(patient_info),
        build_hr_variability_item(patient_info),
        build_breathing_item(stats),
        build_aorta_enhancement_item(stats),
        build_lad_enhancement_item(stats),
        build_rca_enhancement_item(stats),
        build_noise_item(stats),
        build_calcification_item(stats),
        build_step_artifact_item(stats),
        build_ecg_gating_item(patient_info),
        build_range_item(stats, patient_info),
        build_metal_item(stats),
    ]
    return {
        "taskType": "coronary-cta",
        "taskTypeName": "冠脉CTA质控",
        "mock": False,
        "analysisMode": "image-driven",
        "analysisLabel": "影像驱动分析",
        "deterministic": True,
        "modelCode": "coronary_cta_qc_image_rule_v2",
        "modelVersion": "image-rules-2026.03",
        "patientInfo": patient_info,
        "qcItems": qc_items,
        "summary": build_summary(qc_items),
        "duration": 920,
    }


def build_heart_rate_item(patient_info: dict[str, object]) -> dict[str, object]:
    """粗评估心率控制。"""
    heart_rate = parse_int(patient_info.get("heartRate"))
    if heart_rate is None:
        return qc_item("CTA_HR_CONTROL", "心率控制", "待人工确认", "扫描期间平均心率应尽量控制在 75 bpm 以下。", "缺少平均心率信息，需人工确认采集前是否完成心率控制。")
    if heart_rate <= 75:
        return qc_item("CTA_HR_CONTROL", "心率控制", "合格", "扫描期间平均心率应尽量控制在 75 bpm 以下。", f"平均心率 {heart_rate} bpm，满足重建要求。")
    return qc_item("CTA_HR_CONTROL", "心率控制", "不合格", "扫描期间平均心率应尽量控制在 75 bpm 以下。", f"平均心率 {heart_rate} bpm，高于推荐范围。")


def build_hr_variability_item(patient_info: dict[str, object]) -> dict[str, object]:
    """粗评估心率波动。"""
    hr_variability = parse_int(patient_info.get("hrVariability"))
    if hr_variability is None:
        return qc_item("CTA_HR_VARIABILITY", "心率稳定性", "待人工确认", "扫描期间心率波动应尽量控制在 5 bpm 以内。", "缺少心率波动信息，需人工确认门控稳定性。")
    if hr_variability <= 5:
        return qc_item("CTA_HR_VARIABILITY", "心率稳定性", "合格", "扫描期间心率波动应尽量控制在 5 bpm 以内。", f"心率波动 {hr_variability} bpm，满足门控稳定性要求。")
    return qc_item("CTA_HR_VARIABILITY", "心率稳定性", "不合格", "扫描期间心率波动应尽量控制在 5 bpm 以内。", f"心率波动 {hr_variability} bpm，高于推荐范围。")


def build_breathing_item(stats: dict[str, float | int]) -> dict[str, object]:
    """粗评估呼吸伪影。"""
    shift = float(stats["mean_absolute_slice_shift"])
    if shift <= 8.0:
        return qc_item("CTA_BREATHING", "呼吸配合", "合格", "检查过程中应避免明显呼吸运动伪影。", f"层间均值位移 {shift:.1f} HU，呼吸稳定性良好。")
    if shift <= 18.0:
        return qc_item("CTA_BREATHING", "呼吸配合", "待人工确认", "检查过程中应避免明显呼吸运动伪影。", f"层间均值位移 {shift:.1f} HU，建议人工复核膈肌附近层面。")
    return qc_item("CTA_BREATHING", "呼吸配合", "不合格", "检查过程中应避免明显呼吸运动伪影。", f"层间均值位移 {shift:.1f} HU，提示存在明显呼吸运动风险。")


def build_aorta_enhancement_item(stats: dict[str, float | int]) -> dict[str, object]:
    """粗评估主动脉强化。"""
    if float(stats["max_hu"]) >= 300.0 or float(stats["high_density_ratio"]) >= 0.008:
        return qc_item("CTA_AO_ENHANCEMENT", "血管强化 (AO)", "合格", "基于降采样体数据粗评估主动脉强化水平。", f"高密度峰值 {stats['max_hu']:.1f} HU，主动脉强化粗评估达标。")
    if float(stats["max_hu"]) >= 220.0 or float(stats["high_density_ratio"]) >= 0.004:
        return qc_item("CTA_AO_ENHANCEMENT", "血管强化 (AO)", "待人工确认", "基于降采样体数据粗评估主动脉强化水平。", "高密度分布处于边界范围，建议人工复核主动脉强化效果。")
    return qc_item("CTA_AO_ENHANCEMENT", "血管强化 (AO)", "不合格", "基于降采样体数据粗评估主动脉强化水平。", "高密度体素比例偏低，主动脉强化粗评估不足。")


def build_lad_enhancement_item(stats: dict[str, float | int]) -> dict[str, object]:
    """粗评估 LAD 强化。"""
    if float(stats["max_hu"]) >= 260.0 and float(stats["high_density_ratio"]) >= 0.004:
        return qc_item("CTA_LAD_ENHANCEMENT", "血管强化 (LAD)", "合格", "基于降采样体数据粗评估左前降支强化水平。", "高密度分布满足粗评估阈值，左前降支强化基本达标。")
    if float(stats["max_hu"]) >= 220.0 and float(stats["high_density_ratio"]) >= 0.002:
        return qc_item("CTA_LAD_ENHANCEMENT", "血管强化 (LAD)", "待人工确认", "基于降采样体数据粗评估左前降支强化水平。", "左前降支强化处于边界范围，建议结合原始层面人工确认。")
    return qc_item("CTA_LAD_ENHANCEMENT", "血管强化 (LAD)", "不合格", "基于降采样体数据粗评估左前降支强化水平。", "高密度特征偏弱，左前降支强化粗评估不足。")


def build_rca_enhancement_item(stats: dict[str, float | int]) -> dict[str, object]:
    """粗评估 RCA 强化。"""
    if float(stats["max_hu"]) >= 250.0 and float(stats["high_density_ratio"]) >= 0.003:
        return qc_item("CTA_RCA_ENHANCEMENT", "血管强化 (RCA)", "合格", "基于降采样体数据粗评估右冠状动脉强化水平。", "高密度体素比例满足粗评估阈值，右冠强化基本达标。")
    if float(stats["max_hu"]) >= 210.0 and float(stats["high_density_ratio"]) >= 0.0015:
        return qc_item("CTA_RCA_ENHANCEMENT", "血管强化 (RCA)", "待人工确认", "基于降采样体数据粗评估右冠状动脉强化水平。", "右冠强化处于边界范围，建议人工复核近端与远端层面。")
    return qc_item("CTA_RCA_ENHANCEMENT", "血管强化 (RCA)", "不合格", "基于降采样体数据粗评估右冠状动脉强化水平。", "高密度特征偏弱，右冠强化粗评估不足。")


def build_noise_item(stats: dict[str, float | int]) -> dict[str, object]:
    """粗评估噪声水平。"""
    std_hu = float(stats["std_hu"])
    if std_hu <= 550.0:
        return qc_item("CTA_NOISE", "噪声水平", "合格", "降采样体数据标准差用于粗评估整体噪声水平。", f"标准差 {std_hu:.1f} HU，噪声处于可接受范围。")
    if std_hu <= 700.0:
        return qc_item("CTA_NOISE", "噪声水平", "待人工确认", "降采样体数据标准差用于粗评估整体噪声水平。", f"标准差 {std_hu:.1f} HU，建议人工关注远端血管显示。")
    return qc_item("CTA_NOISE", "噪声水平", "不合格", "降采样体数据标准差用于粗评估整体噪声水平。", f"标准差 {std_hu:.1f} HU，整体噪声偏高。")


def build_calcification_item(stats: dict[str, float | int]) -> dict[str, object]:
    """粗评估重钙化影响。"""
    max_hu = float(stats["max_hu"])
    high_density_ratio = float(stats["high_density_ratio"])
    if max_hu >= 2800.0 and high_density_ratio >= 0.01:
        return qc_item("CTA_CALCIFICATION", "钙化积分影响", "不合格", "高密度峰值用于粗评估重钙化对管腔显示的影响。", f"高密度峰值 {max_hu:.1f} HU，提示重钙化可能明显影响管腔评估。")
    if max_hu >= 2400.0:
        return qc_item("CTA_CALCIFICATION", "钙化积分影响", "待人工确认", "高密度峰值用于粗评估重钙化对管腔显示的影响。", "存在较高密度峰值，建议人工确认钙化斑块对管腔评估的影响。")
    return qc_item("CTA_CALCIFICATION", "钙化积分影响", "合格", "高密度峰值用于粗评估重钙化对管腔显示的影响。", "未见明显重钙化高风险特征。")


def build_step_artifact_item(stats: dict[str, float | int]) -> dict[str, object]:
    """粗评估台阶伪影。"""
    shift = float(stats["mean_absolute_slice_shift"])
    if shift <= 8.0:
        return qc_item("CTA_STEP_ARTIFACT", "台阶伪影", "合格", "层间均值位移用于粗评估台阶伪影风险。", "层间位移平稳，未见明显台阶伪影风险。")
    if shift <= 18.0:
        return qc_item("CTA_STEP_ARTIFACT", "台阶伪影", "待人工确认", "层间均值位移用于粗评估台阶伪影风险。", "层间位移存在波动，建议人工复核冠脉连续性。")
    return qc_item("CTA_STEP_ARTIFACT", "台阶伪影", "不合格", "层间均值位移用于粗评估台阶伪影风险。", "层间位移明显，存在台阶伪影高风险。")


def build_ecg_gating_item(patient_info: dict[str, object]) -> dict[str, object]:
    """粗评估心电门控。"""
    recon_phase = normalize_text(patient_info.get("reconPhase"))
    heart_rate = parse_int(patient_info.get("heartRate"))
    if recon_phase:
        return qc_item("CTA_ECG_GATING", "心电门控", "合格", "应提供有效的 ECG 门控或重建相位信息。", f"重建相位 {recon_phase}，门控信息完整。")
    if heart_rate is not None and heart_rate > 75:
        return qc_item("CTA_ECG_GATING", "心电门控", "不合格", "应提供有效的 ECG 门控或重建相位信息。", "缺少重建相位信息且心率偏高，建议人工确认门控质量。")
    return qc_item("CTA_ECG_GATING", "心电门控", "待人工确认", "应提供有效的 ECG 门控或重建相位信息。", "缺少重建相位信息，无法稳定判断门控质量。")


def build_range_item(stats: dict[str, float | int], patient_info: dict[str, object]) -> dict[str, object]:
    """粗评估扫描范围。"""
    depth = int(stats["depth"])
    slice_thickness = float(patient_info.get("sliceThickness") or 0.0)
    if depth >= 150 and 0.0 < slice_thickness <= 1.0:
        return qc_item("CTA_RANGE", "扫描范围", "合格", "应覆盖冠脉诊断所需的心脏范围。", f"体数据深度 {depth} 层、层厚 {slice_thickness:.2f} mm，覆盖范围基本满足要求。")
    if depth >= 120:
        return qc_item("CTA_RANGE", "扫描范围", "待人工确认", "应覆盖冠脉诊断所需的心脏范围。", f"体数据深度 {depth} 层，建议人工复核心脏上下缘是否完整。")
    if slice_thickness > 1.5:
        return qc_item("CTA_RANGE", "扫描范围", "不合格", "应覆盖冠脉诊断所需的心脏范围。", f"层厚 {slice_thickness:.2f} mm 偏大，可能影响冠脉重建与范围判断。")
    return qc_item("CTA_RANGE", "扫描范围", "待人工确认", "应覆盖冠脉诊断所需的心脏范围。", "缺少足够的体数据统计，需人工确认扫描范围。")


def build_metal_item(stats: dict[str, float | int]) -> dict[str, object]:
    """粗评估金属或线束伪影。"""
    max_hu = float(stats["max_hu"])
    if max_hu >= 3200.0:
        return qc_item("CTA_METAL_ARTIFACT", "金属/线束伪影", "不合格", "高密度峰值用于粗评估金属或线束伪影风险。", f"峰值 {max_hu:.1f} HU，提示存在明显金属或线束伪影风险。")
    if max_hu >= 2600.0:
        return qc_item("CTA_METAL_ARTIFACT", "金属/线束伪影", "待人工确认", "高密度峰值用于粗评估金属或线束伪影风险。", "存在较高密度峰值，建议人工复核上腔静脉或电极片附近层面。")
    return qc_item("CTA_METAL_ARTIFACT", "金属/线束伪影", "合格", "高密度峰值用于粗评估金属或线束伪影风险。", "未见明显金属/线束伪影高风险特征。")


def qc_item(item_code: str, name: str, status: str, description: str, detail: str) -> dict[str, object]:
    """构造统一冠脉 CTA 质控项。"""
    return {
        "key": item_code,
        "itemCode": item_code,
        "name": name,
        "status": status,
        "description": description,
        "detail": detail,
    }


def normalize_text(value: object) -> str | None:
    """去空格并把空字符串转为 None。"""
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def parse_int(value: object) -> int | None:
    """安全解析整数。"""
    text = normalize_text(value)
    if text is None:
        return None
    try:
        return int(float(text))
    except Exception:
        return None


def parse_float(value: object) -> float | None:
    """安全解析浮点数。"""
    text = normalize_text(value)
    if text is None:
        return None
    try:
        return round(float(text), 3)
    except Exception:
        return None


def first_non_null(*values: object) -> object | None:
    """返回第一个非空对象。"""
    for value in values:
        if value is not None:
            return value
    return None
