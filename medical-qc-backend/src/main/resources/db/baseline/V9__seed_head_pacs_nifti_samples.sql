INSERT INTO `head_pacs_study_cache` (
  `study_instance_uid`, `patient_id`, `patient_name`, `gender`, `age`, `accession_number`, `study_date`, `study_time`,
  `study_description`, `modality`, `series_count`, `image_count`, `body_part`, `manufacturer`, `model_name`, `image_file_path`,
  `patient_image_path`, `created_at`, `updated_at`
)
VALUES
  ('HEAD-PACS-UID-001', 'HEADP001', '头平扫样例1', '男', 54, 'HEADQC_PACS_001', '2026-03-14', '09:00:00', 'CT头部平扫', 'CT', 1, 64, 'HEAD', 'NIfTI Demo', 'CT Head Plain QC', 'F:/Medical QC SYS/datasets/head_ct_plain_qc/raw/ct/0.nii.gz', NULL, NOW(), NOW()),
  ('HEAD-PACS-UID-002', 'HEADP002', '头平扫样例2', '女', 61, 'HEADQC_PACS_002', '2026-03-14', '09:05:00', 'CT头部平扫', 'CT', 1, 64, 'HEAD', 'NIfTI Demo', 'CT Head Plain QC', 'F:/Medical QC SYS/datasets/head_ct_plain_qc/raw/ct/1.nii.gz', NULL, NOW(), NOW()),
  ('HEAD-PACS-UID-003', 'HEADP003', '头平扫样例3', '男', 47, 'HEADQC_PACS_003', '2026-03-14', '09:10:00', 'CT头部平扫', 'CT', 1, 64, 'HEAD', 'NIfTI Demo', 'CT Head Plain QC', 'F:/Medical QC SYS/datasets/head_ct_plain_qc/raw/ct/2.nii.gz', NULL, NOW(), NOW()),
  ('HEAD-PACS-UID-004', 'HEADP004', '头平扫样例4', '女', 58, 'HEADQC_PACS_004', '2026-03-14', '09:15:00', 'CT头部平扫', 'CT', 1, 64, 'HEAD', 'NIfTI Demo', 'CT Head Plain QC', 'F:/Medical QC SYS/datasets/head_ct_plain_qc/raw/ct/3.nii.gz', NULL, NOW(), NOW()),
  ('HEAD-PACS-UID-005', 'HEADP005', '头平扫样例5', '男', 63, 'HEADQC_PACS_005', '2026-03-14', '09:20:00', 'CT头部平扫', 'CT', 1, 64, 'HEAD', 'NIfTI Demo', 'CT Head Plain QC', 'F:/Medical QC SYS/datasets/head_ct_plain_qc/raw/ct/4.nii.gz', NULL, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  `patient_name` = VALUES(`patient_name`),
  `gender` = VALUES(`gender`),
  `age` = VALUES(`age`),
  `study_date` = VALUES(`study_date`),
  `study_time` = VALUES(`study_time`),
  `study_description` = VALUES(`study_description`),
  `modality` = VALUES(`modality`),
  `series_count` = VALUES(`series_count`),
  `image_count` = VALUES(`image_count`),
  `body_part` = VALUES(`body_part`),
  `manufacturer` = VALUES(`manufacturer`),
  `model_name` = VALUES(`model_name`),
  `image_file_path` = VALUES(`image_file_path`),
  `updated_at` = NOW();
