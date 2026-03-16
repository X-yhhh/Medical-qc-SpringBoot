from __future__ import annotations

import argparse
import csv
import subprocess
from pathlib import Path
import zipfile

import pandas as pd
import SimpleITK as sitk

try:
    from chest_ct_non_contrast_qc.model import DATASET_ROOT, NORMALIZED_DATASET_DIR
except ImportError:
    from model import DATASET_ROOT, NORMALIZED_DATASET_DIR


LUNA_PART1 = "https://zenodo.org/api/records/3723295/files/{name}/content"
LUNA_PART2 = "https://zenodo.org/api/records/4121926/files/{name}/content"
PART1_SUBSETS = {f"subset{i}.zip" for i in range(0, 7)}
PART2_SUBSETS = {f"subset{i}.zip" for i in range(7, 10)}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Download and normalize LUNA16 for chest non contrast QC.")
    parser.add_argument("--subsets", nargs="*", default=["subset0.zip"], help="Subset zip names to download.")
    parser.add_argument("--download-only", action="store_true")
    return parser.parse_args()


def download_file(url: str, output_path: Path) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    if output_path.exists():
        print(f"Skip existing: {output_path.name}")
        return
    subprocess.run(["curl.exe", "-L", url, "--output", str(output_path)], check=True)


def extract_zip(zip_path: Path, destination: Path) -> None:
    destination.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(zip_path, "r") as archive:
        archive.extractall(destination)


def normalize_subset(subset_dir: Path) -> list[dict[str, object]]:
    manifest_rows: list[dict[str, object]] = []
    for mhd_path in sorted(subset_dir.rglob("*.mhd")):
        image = sitk.ReadImage(str(mhd_path))
        case_id = mhd_path.stem
        output_path = NORMALIZED_DATASET_DIR / f"{case_id}.nii.gz"
        if not output_path.exists():
            sitk.WriteImage(image, str(output_path), useCompression=True)
        manifest_rows.append({
            "case_id": case_id,
            "source_path": str(mhd_path),
            "nifti_path": str(output_path),
            "shape": "x".join(str(v) for v in image.GetSize()),
            "spacing": "x".join(str(round(v, 4)) for v in image.GetSpacing()),
            "modality": "CT",
            "study_date": "",
            "task_type": "chest-non-contrast",
        })
    return manifest_rows


def main() -> None:
    args = parse_args()
    raw_root = DATASET_ROOT / "raw" / "luna16"
    normalized_root = NORMALIZED_DATASET_DIR
    normalized_root.mkdir(parents=True, exist_ok=True)

    metadata_files = ["annotations.csv", "candidates.csv"]
    for filename in metadata_files:
        download_file(LUNA_PART1.format(name=filename), raw_root / filename)

    extracted_root = raw_root / "extracted"
    extracted_root.mkdir(parents=True, exist_ok=True)

    for subset_name in args.subsets:
        url_template = LUNA_PART1 if subset_name in PART1_SUBSETS else LUNA_PART2
        zip_path = raw_root / subset_name
        download_file(url_template.format(name=subset_name), zip_path)
        if not args.download_only:
            subset_folder = extracted_root / subset_name.replace(".zip", "")
            # LUNA16 压缩包内部自带 subset 目录，因此统一解压到 extracted 根目录。
            # 若此前留下空目录或不完整解压结果，则在缺少 mhd 文件时重新解压。
            if not subset_folder.exists() or not any(subset_folder.rglob("*.mhd")):
                extract_zip(zip_path, extracted_root)

    if args.download_only:
        return

    manifest_rows: list[dict[str, object]] = []
    for subset_name in args.subsets:
        subset_folder = extracted_root / subset_name.replace(".zip", "")
        manifest_rows.extend(normalize_subset(subset_folder))

    manifest_path = DATASET_ROOT / "manifest.csv"
    pd.DataFrame(manifest_rows).to_csv(manifest_path, index=False, quoting=csv.QUOTE_MINIMAL)
    print(f"Wrote manifest: {manifest_path}")


if __name__ == "__main__":
    main()
