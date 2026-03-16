"""
头部 CT 平扫质控数据集检查与预览生成脚本。

当前该质控项还没有正式的模型训练实现，因此先将数据检查、预览和元数据汇总
统一收口到该目录，便于后续继续补齐 `model.py` 与 `train.py`。
"""

from __future__ import annotations

import argparse
import csv
import json
import math
import re
from collections import Counter
from dataclasses import asdict, dataclass
from pathlib import Path

import nibabel as nib
import numpy as np
from PIL import Image, ImageDraw, ImageOps


MODALITIES = ("ct", "cbct")
RESAMPLING = getattr(Image, "Resampling", Image)
WORKSPACE_ROOT = Path(__file__).resolve().parents[3]
MANAGED_DATASET_ROOT = WORKSPACE_ROOT / "datasets" / "head_ct_plain_qc"


@dataclass
class VolumeStats:
    modality: str
    case_id: str
    file_name: str
    shape_x: int
    shape_y: int
    shape_z: int
    spacing_x_mm: float
    spacing_y_mm: float
    spacing_z_mm: float
    orientation: str
    dtype: str
    intensity_min: float
    intensity_max: float
    intensity_mean: float
    intensity_std: float
    percentile_01: float
    percentile_50: float
    percentile_99: float
    nonzero_fraction: float
    preview_path: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Batch-inspect NIfTI files and generate preview images for the Head-Neck-CBCT-CT dataset."
    )
    parser.add_argument(
        "--dataset-root",
        type=Path,
        default=MANAGED_DATASET_ROOT / "raw",
        help="Dataset root that contains the ct/ and cbct/ folders.",
    )
    parser.add_argument(
        "--output-root",
        type=Path,
        default=MANAGED_DATASET_ROOT / "qc_reports",
        help="Output root for CSV, JSON, and preview images.",
    )
    parser.add_argument(
        "--preview-format",
        choices=("png", "jpg"),
        default="png",
        help="Image format for generated previews.",
    )
    parser.add_argument(
        "--panel-size",
        type=int,
        default=320,
        help="Square size of each preview panel in pixels.",
    )
    parser.add_argument(
        "--limit-per-modality",
        type=int,
        default=None,
        help="Optional debug limit for how many files to process per modality.",
    )
    return parser.parse_args()


def natural_sort_key(path: Path) -> tuple[object, ...]:
    stem = volume_id(path)
    parts = re.split(r"(\d+)", stem)
    key: list[object] = []
    for part in parts:
        if part.isdigit():
            key.append(int(part))
        elif part:
            key.append(part.lower())
    return tuple(key)


def volume_id(path: Path) -> str:
    name = path.name
    if name.endswith(".nii.gz"):
        return name[:-7]
    return path.stem


def find_volume_files(modality_dir: Path) -> list[Path]:
    return sorted(modality_dir.glob("*.nii.gz"), key=natural_sort_key)


def ensure_directories(output_root: Path, preview_format: str) -> dict[str, Path]:
    paths = {
        "metadata": output_root / "volume_metadata.csv",
        "summary": output_root / "summary.json",
        "preview_root": output_root / f"previews_{preview_format}",
    }
    output_root.mkdir(parents=True, exist_ok=True)
    paths["preview_root"].mkdir(parents=True, exist_ok=True)
    for modality in MODALITIES:
        (paths["preview_root"] / modality).mkdir(parents=True, exist_ok=True)
    return paths


def compute_bbox(data: np.ndarray) -> tuple[tuple[int, int], tuple[int, int], tuple[int, int]]:
    mask = data > 0
    if not mask.any():
        return (
            (0, data.shape[0] - 1),
            (0, data.shape[1] - 1),
            (0, data.shape[2] - 1),
        )

    bounds: list[tuple[int, int]] = []
    margins = (8, 8, 2)
    for axis, margin in enumerate(margins):
        active = np.where(mask.any(axis=tuple(i for i in range(3) if i != axis)))[0]
        start = max(int(active[0]) - margin, 0)
        end = min(int(active[-1]) + margin, data.shape[axis] - 1)
        bounds.append((start, end))
    return bounds[0], bounds[1], bounds[2]


def select_mid_indices(bbox: tuple[tuple[int, int], tuple[int, int], tuple[int, int]]) -> tuple[int, int, int]:
    return tuple((start + end) // 2 for start, end in bbox)


def compute_window(data: np.ndarray) -> tuple[float, float]:
    foreground = data[data > 0]
    sample = foreground if foreground.size else data.reshape(-1)

    low = float(np.percentile(sample, 1))
    high = float(np.percentile(sample, 99.5))
    if high <= low:
        low = float(sample.min())
        high = float(sample.max())
    if high <= low:
        high = low + 1.0
    return low, high


def window_to_uint8(slice_2d: np.ndarray, low: float, high: float) -> np.ndarray:
    clipped = np.clip(slice_2d.astype(np.float32), low, high)
    scaled = (clipped - low) / (high - low)
    return np.round(scaled * 255).astype(np.uint8)


def slice_to_image(slice_2d: np.ndarray, low: float, high: float, row_spacing: float, col_spacing: float) -> Image.Image:
    image = Image.fromarray(window_to_uint8(slice_2d, low, high), mode="L")
    target_width = max(1, int(round(image.width * col_spacing)))
    target_height = max(1, int(round(image.height * row_spacing)))
    if (target_width, target_height) != image.size:
        image = image.resize((target_width, target_height), RESAMPLING.BILINEAR)
    return image


def fit_panel(image: Image.Image, panel_size: int) -> Image.Image:
    return ImageOps.pad(
        image.convert("L"),
        (panel_size, panel_size),
        method=RESAMPLING.BILINEAR,
        color=0,
        centering=(0.5, 0.5),
    ).convert("RGB")


def draw_panel(draw: ImageDraw.ImageDraw, x: int, y: int, text: str) -> None:
    draw.rectangle((x, y, x + 100, y + 24), fill=(0, 0, 0))
    draw.text((x + 8, y + 5), text, fill=(255, 255, 255))


def build_preview(
    data: np.ndarray,
    zooms: tuple[float, float, float],
    bbox: tuple[tuple[int, int], tuple[int, int], tuple[int, int]],
    title: str,
    panel_size: int,
) -> Image.Image:
    x_idx, y_idx, z_idx = select_mid_indices(bbox)
    (x0, x1), (y0, y1), (z0, z1) = bbox
    low, high = compute_window(data)

    axial = np.rot90(data[x0 : x1 + 1, y0 : y1 + 1, z_idx])
    coronal = np.rot90(data[x0 : x1 + 1, y_idx, z0 : z1 + 1])
    sagittal = np.rot90(data[x_idx, y0 : y1 + 1, z0 : z1 + 1])

    axial_image = fit_panel(slice_to_image(axial, low, high, zooms[1], zooms[0]), panel_size)
    coronal_image = fit_panel(slice_to_image(coronal, low, high, zooms[2], zooms[0]), panel_size)
    sagittal_image = fit_panel(slice_to_image(sagittal, low, high, zooms[2], zooms[1]), panel_size)

    gap = 20
    header_height = 44
    footer_height = 12
    canvas_width = panel_size * 3 + gap * 4
    canvas_height = panel_size + header_height + footer_height
    canvas = Image.new("RGB", (canvas_width, canvas_height), color=(18, 18, 18))
    draw = ImageDraw.Draw(canvas)
    draw.text((gap, 12), title, fill=(255, 255, 255))

    placements = [
        (axial_image, "Axial"),
        (coronal_image, "Coronal"),
        (sagittal_image, "Sagittal"),
    ]
    for index, (image, label) in enumerate(placements):
        left = gap + index * (panel_size + gap)
        top = header_height
        canvas.paste(image, (left, top))
        draw_panel(draw, left + 8, top + 8, label)

    return canvas


def create_contact_sheet(
    preview_paths: list[Path],
    output_path: Path,
    title: str,
    thumb_size: int = 160,
    columns: int = 5,
) -> None:
    if not preview_paths:
        return

    gap = 16
    header_height = 48
    rows = math.ceil(len(preview_paths) / columns)
    canvas_width = columns * thumb_size + (columns + 1) * gap
    canvas_height = rows * thumb_size + (rows + 1) * gap + header_height
    canvas = Image.new("RGB", (canvas_width, canvas_height), color=(24, 24, 24))
    draw = ImageDraw.Draw(canvas)
    draw.text((gap, 16), title, fill=(255, 255, 255))

    for index, preview_path in enumerate(preview_paths):
        row = index // columns
        col = index % columns
        left = gap + col * (thumb_size + gap)
        top = header_height + gap + row * (thumb_size + gap)
        with Image.open(preview_path) as preview:
            tile = ImageOps.fit(
                preview.convert("RGB"),
                (thumb_size, thumb_size),
                method=RESAMPLING.BILINEAR,
                centering=(0.5, 0.5),
            )
        canvas.paste(tile, (left, top))
        label = preview_path.stem
        draw.rectangle((left, top + thumb_size - 26, left + thumb_size, top + thumb_size), fill=(0, 0, 0))
        draw.text((left + 8, top + thumb_size - 20), label, fill=(255, 255, 255))

    canvas.save(output_path)


def collect_stats(modality: str, volume_path: Path, preview_path: Path, panel_size: int) -> VolumeStats:
    image = nib.load(str(volume_path))
    data = np.asanyarray(image.dataobj)
    zooms = tuple(float(value) for value in image.header.get_zooms()[:3])
    bbox = compute_bbox(data)
    case_id = volume_id(volume_path)
    title = f"{modality.upper()} | {case_id}"
    preview_image = build_preview(data, zooms, bbox, title, panel_size)
    preview_image.save(preview_path)

    flat = data.astype(np.float32, copy=False).reshape(-1)
    return VolumeStats(
        modality=modality,
        case_id=case_id,
        file_name=volume_path.name,
        shape_x=int(image.shape[0]),
        shape_y=int(image.shape[1]),
        shape_z=int(image.shape[2]),
        spacing_x_mm=zooms[0],
        spacing_y_mm=zooms[1],
        spacing_z_mm=zooms[2],
        orientation="".join(nib.aff2axcodes(image.affine)),
        dtype=str(data.dtype),
        intensity_min=float(flat.min()),
        intensity_max=float(flat.max()),
        intensity_mean=float(flat.mean()),
        intensity_std=float(flat.std()),
        percentile_01=float(np.percentile(flat, 1)),
        percentile_50=float(np.percentile(flat, 50)),
        percentile_99=float(np.percentile(flat, 99)),
        nonzero_fraction=float(np.count_nonzero(flat) / flat.size),
        preview_path=str(preview_path),
    )


def write_metadata_csv(rows: list[VolumeStats], output_path: Path) -> None:
    fieldnames = list(asdict(rows[0]).keys()) if rows else [field.name for field in VolumeStats.__dataclass_fields__.values()]
    with output_path.open("w", newline="", encoding="utf-8") as file:
        writer = csv.DictWriter(file, fieldnames=fieldnames)
        writer.writeheader()
        for row in rows:
            writer.writerow(asdict(row))


def build_summary(rows: list[VolumeStats]) -> dict[str, object]:
    summary: dict[str, object] = {"total_files": len(rows), "modalities": {}}
    for modality in MODALITIES:
        modality_rows = [row for row in rows if row.modality == modality]
        shapes = sorted({(row.shape_x, row.shape_y, row.shape_z) for row in modality_rows})
        spacings = sorted({(row.spacing_x_mm, row.spacing_y_mm, row.spacing_z_mm) for row in modality_rows})
        orientation_counts = Counter(row.orientation for row in modality_rows)
        summary["modalities"][modality] = {
            "count": len(modality_rows),
            "unique_shapes": [list(shape) for shape in shapes],
            "unique_spacings_mm": [list(spacing) for spacing in spacings],
            "orientation_counts": dict(orientation_counts),
            "intensity_min": min((row.intensity_min for row in modality_rows), default=None),
            "intensity_max": max((row.intensity_max for row in modality_rows), default=None),
            "mean_nonzero_fraction": round(
                sum(row.nonzero_fraction for row in modality_rows) / len(modality_rows), 6
            )
            if modality_rows
            else None,
        }
    return summary


def main() -> None:
    args = parse_args()
    paths = ensure_directories(args.output_root, args.preview_format)

    rows: list[VolumeStats] = []
    preview_groups: dict[str, list[Path]] = {modality: [] for modality in MODALITIES}

    for modality in MODALITIES:
        modality_dir = args.dataset_root / modality
        if not modality_dir.exists():
            raise FileNotFoundError(f"Missing modality directory: {modality_dir}")

        volume_paths = find_volume_files(modality_dir)
        if args.limit_per_modality is not None:
            volume_paths = volume_paths[: args.limit_per_modality]

        for volume_path in volume_paths:
            preview_path = paths["preview_root"] / modality / f"{volume_id(volume_path)}.{args.preview_format}"
            stats = collect_stats(modality, volume_path, preview_path, args.panel_size)
            rows.append(stats)
            preview_groups[modality].append(preview_path)

    write_metadata_csv(rows, paths["metadata"])

    summary = build_summary(rows)
    with paths["summary"].open("w", encoding="utf-8") as file:
        json.dump(summary, file, indent=2, ensure_ascii=False)

    for modality, preview_paths in preview_groups.items():
        create_contact_sheet(
            preview_paths,
            args.output_root / f"{modality}_overview.png",
            title=f"{modality.upper()} overview ({len(preview_paths)} files)",
        )

    print(f"Metadata CSV: {paths['metadata']}")
    print(f"Summary JSON: {paths['summary']}")
    for modality in MODALITIES:
        print(f"Preview folder ({modality}): {paths['preview_root'] / modality}")
        print(f"Overview image ({modality}): {args.output_root / f'{modality}_overview.png'}")


if __name__ == "__main__":
    main()
