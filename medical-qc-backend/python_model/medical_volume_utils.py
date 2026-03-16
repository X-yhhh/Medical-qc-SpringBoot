"""
统一医学影像输入工具。

负责把 NIfTI、DICOM 文件、DICOM ZIP 统一转换成当前推理链可消费的 NIfTI，
并抽取患者、设备和扫描参数元数据。
"""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
import shutil
import tempfile
import zipfile

import nibabel as nib
import numpy as np
import pydicom
import SimpleITK as sitk


@dataclass
class ManagedMedicalInput:
    """封装影像解析结果和临时目录生命周期。"""

    source_path: Path
    analysis_path: Path
    patient_info: dict[str, object]
    metadata: dict[str, object] = field(default_factory=dict)
    cleanup_paths: list[Path] = field(default_factory=list)

    def cleanup(self) -> None:
        """删除中间解压目录和临时 NIfTI。"""
        for cleanup_path in self.cleanup_paths:
            shutil.rmtree(cleanup_path, ignore_errors=True)


def prepare_volume_input(input_path: str | Path) -> ManagedMedicalInput:
    """把输入路径规范化为现有推理链统一使用的 NIfTI 路径。"""
    source_path = Path(str(input_path)).expanduser().resolve()
    if not source_path.exists():
        raise FileNotFoundError(f"医学影像输入不存在: {source_path}")

    if is_nifti_path(source_path):
        return ManagedMedicalInput(
            source_path=source_path,
            analysis_path=source_path,
            patient_info=build_nifti_patient_info(source_path),
        )

    working_dir = Path(tempfile.mkdtemp(prefix="medical_qc_input_"))
    dicom_root = source_path
    if source_path.is_file() and source_path.suffix.lower() == ".zip":
        dicom_root = extract_zip_to_dir(source_path, working_dir / "zip")

    dicom_files = resolve_dicom_series_files(dicom_root)
    if not dicom_files:
        raise ValueError(f"未解析到可用 DICOM 系列: {source_path}")

    volume, spacing = load_dicom_volume(dicom_files)
    metadata = extract_dicom_metadata(dicom_files)
    analysis_path = write_temp_nifti(volume, spacing, working_dir / "series.nii.gz")
    patient_info = build_dicom_patient_info(source_path, volume, spacing, metadata)
    return ManagedMedicalInput(
        source_path=source_path,
        analysis_path=analysis_path,
        patient_info=patient_info,
        metadata=metadata,
        cleanup_paths=[working_dir],
    )


def read_nifti_stats(volume_path: Path) -> dict[str, float | int]:
    """读取 NIfTI 体数据的轻量统计，供规则分析链路复用。"""
    image = nib.load(str(volume_path))
    volume = np.asanyarray(image.dataobj).astype(np.float32, copy=False)
    volume_zyx = np.transpose(volume, (2, 1, 0))
    zooms = tuple(float(value) for value in image.header.get_zooms()[:3])
    flattened = volume_zyx.reshape(-1)
    slice_means = volume_zyx.mean(axis=(1, 2)) if volume_zyx.ndim == 3 else np.array([0.0])
    mean_slice_shift = float(np.mean(np.abs(np.diff(slice_means)))) if slice_means.size > 1 else 0.0
    return {
        "width": int(volume_zyx.shape[2]) if volume_zyx.ndim == 3 else 0,
        "height": int(volume_zyx.shape[1]) if volume_zyx.ndim == 3 else 0,
        "depth": int(volume_zyx.shape[0]) if volume_zyx.ndim == 3 else 0,
        "spacing_x": round(zooms[0], 3) if len(zooms) >= 1 else 0.0,
        "spacing_y": round(zooms[1], 3) if len(zooms) >= 2 else 0.0,
        "spacing_z": round(zooms[2], 3) if len(zooms) >= 3 else 0.0,
        "mean_hu": round(float(flattened.mean()), 3) if flattened.size else 0.0,
        "std_hu": round(float(flattened.std()), 3) if flattened.size else 0.0,
        "max_hu": round(float(flattened.max()), 3) if flattened.size else 0.0,
        "high_density_ratio": round(float(np.mean(flattened >= 250.0)), 6) if flattened.size else 0.0,
        "mean_absolute_slice_shift": round(mean_slice_shift, 3),
    }


def enrich_result_with_medical_input(result: dict[str, object], managed_input: ManagedMedicalInput, task_type: str) -> dict[str, object]:
    """把统一影像输入提取的元数据补齐到推理结果。"""
    enriched = dict(result or {})
    patient_info = dict(enriched.get("patientInfo") or {})
    for key, value in managed_input.patient_info.items():
        if value not in (None, "", [], {}):
            patient_info[key] = value
    ctdi_vol = managed_input.metadata.get("ctdi_vol")
    if ctdi_vol is not None:
        patient_info["ctdiVol"] = ctdi_vol
    enriched["patientInfo"] = patient_info

    qc_items = [dict(item) for item in (enriched.get("qcItems") or [])]
    if task_type == "head" and ctdi_vol is not None:
        apply_head_ctdi_rule(qc_items, float(ctdi_vol))
    enriched["qcItems"] = qc_items
    enriched["summary"] = build_summary(qc_items)
    enriched["qcStatus"] = enriched["summary"]["result"]
    enriched["qualityScore"] = enriched["summary"]["qualityScore"]
    enriched["abnormalCount"] = enriched["summary"]["abnormalCount"]
    enriched["primaryIssue"] = next((item["name"] for item in qc_items if item.get("status") == "不合格"), "未见明显异常")
    return enriched


def build_summary(qc_items: list[dict[str, object]]) -> dict[str, object]:
    """按统一状态规则计算质控摘要。"""
    total_items = len(qc_items)
    pass_count = sum(1 for item in qc_items if item.get("status") == "合格")
    fail_count = sum(1 for item in qc_items if item.get("status") == "不合格")
    review_count = sum(1 for item in qc_items if item.get("status") == "待人工确认")
    abnormal_count = fail_count + review_count
    quality_score = int(round(pass_count * 100.0 / max(total_items, 1)))
    result = "不合格" if fail_count > 0 else ("待人工确认" if review_count > 0 else "合格")
    return {
        "totalItems": total_items,
        "passCount": pass_count,
        "failCount": fail_count,
        "reviewCount": review_count,
        "abnormalCount": abnormal_count,
        "qualityScore": quality_score,
        "result": result,
    }


def is_nifti_path(file_path: Path) -> bool:
    """判断是否为 NIfTI 文件。"""
    lower_name = file_path.name.lower()
    return lower_name.endswith(".nii") or lower_name.endswith(".nii.gz")


def extract_zip_to_dir(zip_path: Path, target_dir: Path) -> Path:
    """解压 ZIP 影像包。"""
    target_dir.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(zip_path, "r") as archive:
        archive.extractall(target_dir)
    return target_dir


def resolve_dicom_series_files(input_root: Path) -> list[Path]:
    """递归收集 DICOM 文件，并选择文件数最多的系列。"""
    if input_root.is_file() and input_root.suffix.lower() in {".dcm", ".dicom"}:
        return [input_root]

    series_groups: dict[str, list[Path]] = {}
    for file_path in input_root.rglob("*"):
        if not file_path.is_file():
            continue
        try:
            dataset = pydicom.dcmread(str(file_path), stop_before_pixels=True, force=True)
        except Exception:
            continue
        series_uid = normalize_text(getattr(dataset, "SeriesInstanceUID", None)) or "__single__"
        series_groups.setdefault(series_uid, []).append(file_path)

    if not series_groups:
        return []
    selected_files = max(series_groups.values(), key=len)
    return sorted(selected_files, key=sort_dicom_file_key)


def load_dicom_volume(dicom_files: list[Path]) -> tuple[np.ndarray, tuple[float, float, float]]:
    """读取 DICOM 体数据并返回 Z,Y,X 数组和 spacing。"""
    if len(dicom_files) == 1:
        dataset = pydicom.dcmread(str(dicom_files[0]), force=True)
        pixels = dataset.pixel_array.astype(np.float32, copy=False)
        volume = pixels[None, ...] if pixels.ndim == 2 else pixels
        slope = float(getattr(dataset, "RescaleSlope", 1.0) or 1.0)
        intercept = float(getattr(dataset, "RescaleIntercept", 0.0) or 0.0)
        pixel_spacing = getattr(dataset, "PixelSpacing", [1.0, 1.0])
        slice_thickness = float(getattr(dataset, "SliceThickness", 1.0) or 1.0)
        return volume * slope + intercept, (float(pixel_spacing[0]), float(pixel_spacing[1]), slice_thickness)

    reader = sitk.ImageSeriesReader()
    reader.SetFileNames([str(file_path) for file_path in dicom_files])
    image = reader.Execute()
    volume = sitk.GetArrayFromImage(image).astype(np.float32, copy=False)
    spacing = image.GetSpacing()
    return volume, (float(spacing[0]), float(spacing[1]), float(spacing[2]))


def write_temp_nifti(volume_zyx: np.ndarray, spacing: tuple[float, float, float], output_path: Path) -> Path:
    """把 Z,Y,X 体数据临时写成 NIfTI。"""
    affine = np.diag([spacing[0], spacing[1], spacing[2], 1.0])
    image = nib.Nifti1Image(np.transpose(volume_zyx, (2, 1, 0)).astype(np.float32, copy=False), affine)
    nib.save(image, str(output_path))
    return output_path


def build_nifti_patient_info(volume_path: Path) -> dict[str, object]:
    """从 NIfTI 头构建患者和采集信息。"""
    image = nib.load(str(volume_path))
    zooms = tuple(float(value) for value in image.header.get_zooms()[:3])
    case_id = volume_path.name[:-7] if volume_path.name.endswith(".nii.gz") else volume_path.stem
    return {
        "name": case_id,
        "gender": "未知",
        "age": 0,
        "studyId": case_id,
        "accessionNumber": case_id,
        "studyDate": "",
        "device": "NIfTI import",
        "sliceCount": int(image.shape[2]) if len(image.shape) >= 3 else 0,
        "sliceThickness": round(zooms[2], 3) if len(zooms) >= 3 else 0.0,
        "pixelSpacing": [round(zooms[0], 3), round(zooms[1], 3)],
        "sourceMode": "local",
        "originalFilename": volume_path.name,
        "dataFormat": "NIFTI",
    }


def build_dicom_patient_info(source_path: Path,
                             volume_zyx: np.ndarray,
                             spacing: tuple[float, float, float],
                             metadata: dict[str, object]) -> dict[str, object]:
    """根据 DICOM 元数据和体数据构建患者与采集信息。"""
    patient_info = {
        "name": normalize_text(metadata.get("patient_name")) or source_path.stem,
        "patientId": normalize_text(metadata.get("patient_id")),
        "gender": normalize_text(metadata.get("gender")) or "未知",
        "age": metadata.get("age") or 0,
        "studyId": normalize_text(metadata.get("accession_number")) or source_path.stem,
        "accessionNumber": normalize_text(metadata.get("accession_number")) or source_path.stem,
        "studyDate": normalize_text(metadata.get("study_date")) or "",
        "device": first_non_blank(metadata.get("manufacturer"), metadata.get("device_model"), "DICOM import"),
        "sliceCount": int(volume_zyx.shape[0]) if volume_zyx.ndim == 3 else 0,
        "sliceThickness": round(float(metadata.get("slice_thickness") or spacing[2]), 3),
        "pixelSpacing": [round(float(spacing[0]), 3), round(float(spacing[1]), 3)],
        "sourceMode": "local",
        "originalFilename": source_path.name,
        "dataFormat": "DICOM",
    }
    if metadata.get("heart_rate") is not None:
        patient_info["heartRate"] = metadata.get("heart_rate")
    if metadata.get("kvp") is not None:
        patient_info["kVp"] = metadata.get("kvp")
    if metadata.get("contrast_volume") is not None:
        patient_info["contrastVolume"] = metadata.get("contrast_volume")
    if metadata.get("flow_rate") is not None:
        patient_info["flowRate"] = metadata.get("flow_rate")
    if metadata.get("recon_phase") is not None:
        patient_info["reconPhase"] = metadata.get("recon_phase")
    return patient_info


def extract_dicom_metadata(dicom_files: list[Path]) -> dict[str, object]:
    """从 DICOM 头提取统一元数据。"""
    if not dicom_files:
        return {}
    dataset = pydicom.dcmread(str(dicom_files[0]), stop_before_pixels=True, force=True)
    pixel_spacing = getattr(dataset, "PixelSpacing", None)
    metadata = {
        "patient_name": normalize_person_name(getattr(dataset, "PatientName", None)),
        "patient_id": normalize_text(getattr(dataset, "PatientID", None)),
        "accession_number": normalize_text(getattr(dataset, "AccessionNumber", None)),
        "study_date": normalize_date(getattr(dataset, "StudyDate", None)),
        "gender": normalize_text(getattr(dataset, "PatientSex", None)),
        "age": parse_dicom_age(normalize_text(getattr(dataset, "PatientAge", None))),
        "manufacturer": normalize_text(getattr(dataset, "Manufacturer", None)),
        "device_model": normalize_text(getattr(dataset, "ManufacturerModelName", None)),
        "slice_thickness": parse_float(getattr(dataset, "SliceThickness", None)),
        "pixel_spacing": [parse_float(pixel_spacing[0]), parse_float(pixel_spacing[1])] if pixel_spacing and len(pixel_spacing) >= 2 else None,
        "ctdi_vol": parse_float(getattr(dataset, "CTDIvol", None)),
        "kvp": normalize_text(getattr(dataset, "KVP", None)),
        "heart_rate": parse_int(getattr(dataset, "HeartRate", None)),
        "contrast_volume": parse_int(first_non_blank(getattr(dataset, "ContrastBolusVolume", None), getattr(dataset, "ContrastFlowVolume", None))),
        "flow_rate": parse_float(first_non_blank(getattr(dataset, "ContrastFlowRate", None), getattr(dataset, "ContrastBolusFlowRate", None))),
        "recon_phase": normalize_text(getattr(dataset, "CardiacRRIntervalSpecified", None)),
    }
    return {key: value for key, value in metadata.items() if value not in (None, "", [], {})}


def apply_head_ctdi_rule(qc_items: list[dict[str, object]], ctdi_vol: float) -> None:
    """用真实 CTDIvol 替换头部剂量代理项。"""
    for item in qc_items:
        if item.get("key") != "dose_proxy":
            continue
        item["name"] = "剂量控制 (CTDIvol)"
        item["description"] = "优先使用 DICOM CTDIvol 标签评估扫描剂量"
        item["threshold"] = "CTDIvol<=60mGy"
        item["ruleBased"] = True
        if ctdi_vol <= 60.0:
            item["status"] = "合格"
            item["detail"] = f"CTDIvol {ctdi_vol:.1f} mGy，处于参考范围内"
            item["failProbability"] = 0.0
            item["passProbability"] = 1.0
        elif ctdi_vol <= 80.0:
            item["status"] = "待人工确认"
            item["detail"] = f"CTDIvol {ctdi_vol:.1f} mGy，略高于参考值，建议人工复核协议设置"
            item["failProbability"] = 0.5
            item["passProbability"] = 0.5
        else:
            item["status"] = "不合格"
            item["detail"] = f"CTDIvol {ctdi_vol:.1f} mGy，明显高于参考值"
            item["failProbability"] = 1.0
            item["passProbability"] = 0.0
        return


def sort_dicom_file_key(file_path: Path) -> tuple[int, str]:
    """按 InstanceNumber 优先排序。"""
    try:
        dataset = pydicom.dcmread(str(file_path), stop_before_pixels=True, force=True)
        return int(getattr(dataset, "InstanceNumber", 0) or 0), file_path.name
    except Exception:
        return 0, file_path.name


def normalize_person_name(value: object) -> str | None:
    """把 DICOM PersonName 规范为普通文本。"""
    if value is None:
        return None
    text = str(value).replace("^", " ").strip()
    return text or None


def normalize_text(value: object) -> str | None:
    """去空格并把空字符串转为 None。"""
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def normalize_date(value: object) -> str | None:
    """把 YYYYMMDD 转成 YYYY-MM-DD。"""
    text = normalize_text(value)
    if text is None or len(text) != 8 or not text.isdigit():
        return text
    return f"{text[0:4]}-{text[4:6]}-{text[6:8]}"


def parse_dicom_age(value: str | None) -> int | None:
    """解析 PatientAge。"""
    if value is None:
        return None
    digits = "".join(character for character in value if character.isdigit())
    if not digits:
        return None
    return int(digits)


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


def first_non_blank(*values: object) -> str | None:
    """返回第一个非空文本。"""
    for value in values:
        text = normalize_text(value)
        if text is not None:
            return text
    return None
