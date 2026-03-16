"""
CT胸部平扫数据集检查脚本。
"""

from __future__ import annotations

from pathlib import Path

import nibabel as nib


WORKSPACE_ROOT = Path(__file__).resolve().parents[3]
DATASET_DIR = WORKSPACE_ROOT / "datasets" / "chest_ct_non_contrast_qc" / "normalized"


def main() -> None:
    files = sorted(DATASET_DIR.glob("*.nii.gz"))
    print(f"files={len(files)}")
    if not files:
        return
    image = nib.load(str(files[0]))
    print(f"sample={files[0].name}")
    print(f"shape={image.shape}")
    print(f"zooms={image.header.get_zooms()[:3]}")


if __name__ == "__main__":
    main()
