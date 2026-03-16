"""
CT 胸部增强质控的影像驱动分析模块。

当前版本仍以规则分析为主，但直接基于影像体数据和采集参数生成结构化结果，
不再依赖 Java 侧 mock 结果。
"""

from __future__ import annotations

from pathlib import Path

from medical_volume_utils import build_summary, read_nifti_stats


def run_inference(input_path: Path, metadata: dict[str, object] | None = None) -> dict[str, object]:
    """对单个胸部增强检查执行影像驱动分析。"""
    metadata = metadata or {}
    stats = read_nifti_stats(input_path)
    patient_info = {
        "flowRate": parse_float(metadata.get("flow_rate")),
        "contrastVolume": parse_int(metadata.get("contrast_volume")),
        "injectionSite": normalize_text(metadata.get("injection_site")),
        "sliceThickness": first_non_null(parse_float(metadata.get("slice_thickness")), stats["spacing_z"]),
        "bolusTrackingHu": parse_int(metadata.get("bolus_tracking_hu")),
        "scanDelaySec": parse_int(metadata.get("scan_delay_sec")),
        "sliceCount": stats["depth"],
        "pixelSpacing": [stats["spacing_x"], stats["spacing_y"]],
    }

    qc_items = [
        build_range_item(stats, patient_info),
        build_breathing_item(stats),
        build_metal_item(stats),
        build_aorta_enhancement_item(stats, patient_info),
        build_pulmonary_enhancement_item(stats, patient_info),
        build_venous_contamination_item(stats, patient_info),
        build_parenchyma_uniformity_item(stats, patient_info),
    ]
    return {
        "taskType": "chest-contrast",
        "taskTypeName": "CT胸部增强质控",
        "mock": False,
        "analysisMode": "image-driven",
        "analysisLabel": "影像驱动分析",
        "deterministic": True,
        "modelCode": "chest_contrast_qc_image_rule_v2",
        "modelVersion": "image-rules-2026.03",
        "patientInfo": patient_info,
        "qcItems": qc_items,
        "summary": build_summary(qc_items),
        "duration": 850,
    }


def build_range_item(stats: dict[str, float | int], patient_info: dict[str, object]) -> dict[str, object]:
    """根据层厚和层数粗评估扫描覆盖范围。"""
    depth = int(stats["depth"])
    slice_thickness = float(patient_info.get("sliceThickness") or 0.0)
    if depth >= 150 and 0.0 < slice_thickness <= 2.0:
        return qc_item("CHEST_CONTRAST_RANGE", "定位像范围", "合格", "定位像应覆盖肺尖至肺底完整范围。", f"体数据深度 {depth} 层、层厚 {slice_thickness:.2f} mm，覆盖范围基本满足要求。", "定位片")
    if depth >= 120:
        return qc_item("CHEST_CONTRAST_RANGE", "定位像范围", "待人工确认", "定位像应覆盖肺尖至肺底完整范围。", f"体数据深度 {depth} 层，建议人工确认肺尖与肺底是否完整覆盖。", "定位片")
    return qc_item("CHEST_CONTRAST_RANGE", "定位像范围", "不合格", "定位像应覆盖肺尖至肺底完整范围。", f"体数据深度仅 {depth} 层，存在扫描范围不足风险。", "定位片")


def build_breathing_item(stats: dict[str, float | int]) -> dict[str, object]:
    """根据层间位移粗评估呼吸配合。"""
    shift = float(stats["mean_absolute_slice_shift"])
    if shift <= 8.0:
        return qc_item("CHEST_CONTRAST_BREATHING", "呼吸配合", "合格", "增强扫描应避免明显呼吸运动伪影。", f"层间均值位移 {shift:.1f} HU，呼吸稳定性良好。", "平扫期")
    if shift <= 18.0:
        return qc_item("CHEST_CONTRAST_BREATHING", "呼吸配合", "待人工确认", "增强扫描应避免明显呼吸运动伪影。", f"层间均值位移 {shift:.1f} HU，建议人工复核膈肌附近层面。", "平扫期")
    return qc_item("CHEST_CONTRAST_BREATHING", "呼吸配合", "不合格", "增强扫描应避免明显呼吸运动伪影。", f"层间均值位移 {shift:.1f} HU，提示存在明显呼吸运动风险。", "平扫期")


def build_metal_item(stats: dict[str, float | int]) -> dict[str, object]:
    """根据高密度峰值粗评估金属伪影。"""
    max_hu = float(stats["max_hu"])
    if max_hu >= 3000.0:
        return qc_item("CHEST_CONTRAST_METAL", "金属伪影", "不合格", "应避免金属植入物或外来异物对增强序列产生明显伪影。", f"峰值 {max_hu:.1f} HU，提示存在明显金属伪影高风险。", "平扫期")
    if max_hu >= 2400.0:
        return qc_item("CHEST_CONTRAST_METAL", "金属伪影", "待人工确认", "应避免金属植入物或外来异物对增强序列产生明显伪影。", f"峰值 {max_hu:.1f} HU，建议人工复核高密度伪影影响范围。", "平扫期")
    return qc_item("CHEST_CONTRAST_METAL", "金属伪影", "合格", "应避免金属植入物或外来异物对增强序列产生明显伪影。", "未见明显金属伪影高风险特征。", "平扫期")


def build_aorta_enhancement_item(stats: dict[str, float | int], patient_info: dict[str, object]) -> dict[str, object]:
    """根据高密度比例和 bolus tracking 阈值粗评估主动脉强化。"""
    bolus_tracking_hu = parse_int(patient_info.get("bolusTrackingHu"))
    high_density_ratio = float(stats["high_density_ratio"])
    max_hu = float(stats["max_hu"])
    if bolus_tracking_hu is not None and bolus_tracking_hu >= 250:
        return qc_item("CHEST_CONTRAST_AORTA_ENHANCEMENT", "主动脉强化值", "合格", "主动脉弓强化应达到诊断要求。", f"Bolus tracking 阈值 {bolus_tracking_hu} HU，达到推荐门限。", "增强I期")
    if max_hu >= 320.0 or high_density_ratio >= 0.008:
        return qc_item("CHEST_CONTRAST_AORTA_ENHANCEMENT", "主动脉强化值", "合格", "主动脉弓强化应达到诊断要求。", f"高密度峰值 {max_hu:.1f} HU，主动脉强化粗评估达标。", "增强I期")
    if max_hu >= 240.0 or high_density_ratio >= 0.004:
        return qc_item("CHEST_CONTRAST_AORTA_ENHANCEMENT", "主动脉强化值", "待人工确认", "主动脉弓强化应达到诊断要求。", "高密度分布处于边界范围，建议人工复核主动脉强化效果。", "增强I期")
    return qc_item("CHEST_CONTRAST_AORTA_ENHANCEMENT", "主动脉强化值", "不合格", "主动脉弓强化应达到诊断要求。", "高密度体素比例偏低，主动脉强化粗评估不足。", "增强I期")


def build_pulmonary_enhancement_item(stats: dict[str, float | int], patient_info: dict[str, object]) -> dict[str, object]:
    """根据流速、总量和峰值粗评估肺动脉强化。"""
    flow_rate = parse_float(patient_info.get("flowRate"))
    contrast_volume = parse_int(patient_info.get("contrastVolume"))
    if flow_rate is not None and contrast_volume is not None and flow_rate >= 3.5 and contrast_volume >= 60:
        return qc_item("CHEST_CONTRAST_PULMONARY_ENHANCEMENT", "肺动脉强化", "合格", "肺动脉主干强化应达到诊断要求。", f"流速 {flow_rate:.1f} mL/s、总量 {contrast_volume} mL，协议满足推荐区间。", "增强I期")
    if float(stats["max_hu"]) >= 260.0:
        return qc_item("CHEST_CONTRAST_PULMONARY_ENHANCEMENT", "肺动脉强化", "待人工确认", "肺动脉主干强化应达到诊断要求。", "峰值达到边界范围，建议人工确认肺动脉主干强化。", "增强I期")
    return qc_item("CHEST_CONTRAST_PULMONARY_ENHANCEMENT", "肺动脉强化", "不合格", "肺动脉主干强化应达到诊断要求。", "流速/总量或高密度分布不足，肺动脉强化存在不足风险。", "增强I期")


def build_venous_contamination_item(stats: dict[str, float | int], patient_info: dict[str, object]) -> dict[str, object]:
    """根据极高密度峰值和扫描延迟粗评估静脉污染。"""
    max_hu = float(stats["max_hu"])
    scan_delay = parse_int(patient_info.get("scanDelaySec"))
    if scan_delay is not None and scan_delay < 10:
        return qc_item("CHEST_CONTRAST_VENOUS_CONTAMINATION", "静脉污染", "不合格", "应尽量降低上腔静脉或注射静脉高浓度对比剂污染。", "存在极高密度峰值或扫描延迟偏短，提示静脉污染风险偏高。", "增强I期")
    if max_hu >= 2600.0:
        return qc_item("CHEST_CONTRAST_VENOUS_CONTAMINATION", "静脉污染", "待人工确认", "应尽量降低上腔静脉或注射静脉高浓度对比剂污染。", "存在较高密度峰值，建议人工复核上腔静脉附近层面。", "增强I期")
    return qc_item("CHEST_CONTRAST_VENOUS_CONTAMINATION", "静脉污染", "合格", "应尽量降低上腔静脉或注射静脉高浓度对比剂污染。", "未见明显静脉污染高风险特征。", "增强I期")


def build_parenchyma_uniformity_item(stats: dict[str, float | int], patient_info: dict[str, object]) -> dict[str, object]:
    """根据噪声水平和延迟参数粗评估实质强化均匀度。"""
    std_hu = float(stats["std_hu"])
    scan_delay = parse_int(patient_info.get("scanDelaySec"))
    contrast_volume = parse_int(patient_info.get("contrastVolume"))
    if contrast_volume is not None and contrast_volume >= 70 and (scan_delay is None or scan_delay >= 45) and std_hu <= 550.0:
        return qc_item("CHEST_CONTRAST_PARENCHYMA_UNIFORMITY", "实质强化均匀度", "合格", "延迟期应保证实质强化分布较均匀。", f"体数据标准差 {std_hu:.1f} HU，强化分布整体较均匀。", "增强II期")
    if std_hu <= 700.0:
        return qc_item("CHEST_CONTRAST_PARENCHYMA_UNIFORMITY", "实质强化均匀度", "待人工确认", "延迟期应保证实质强化分布较均匀。", f"体数据标准差 {std_hu:.1f} HU，建议人工复核延迟期强化均匀度。", "增强II期")
    return qc_item("CHEST_CONTRAST_PARENCHYMA_UNIFORMITY", "实质强化均匀度", "不合格", "延迟期应保证实质强化分布较均匀。", f"体数据标准差 {std_hu:.1f} HU，提示强化分布不均或噪声偏高。", "增强II期")


def qc_item(item_code: str, name: str, status: str, description: str, detail: str, phase: str) -> dict[str, object]:
    """构造统一胸部增强质控项。"""
    return {
        "key": item_code,
        "itemCode": item_code,
        "name": name,
        "status": status,
        "description": description,
        "detail": detail,
        "phase": phase,
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
