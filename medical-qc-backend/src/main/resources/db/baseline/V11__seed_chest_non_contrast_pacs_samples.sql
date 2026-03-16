INSERT INTO `chest_non_contrast_pacs_study_cache` (
  `study_instance_uid`, `patient_id`, `patient_name`, `gender`, `age`, `accession_number`, `study_date`, `study_time`,
  `study_description`, `modality`, `series_count`, `image_count`, `body_part`, `manufacturer`, `model_name`,
  `image_file_path`, `patient_image_path`, `created_at`, `updated_at`
)
VALUES
  ('CHEST-NC-UID-001', 'CHESTNC001', '胸部平扫样例1', '男', 58, 'CHESTNC_PACS_001', '2026-03-14', '10:00:00', 'CT胸部平扫', 'CT', 1, 300, 'CHEST', 'LUNA16 Demo', 'Chest CT Non Contrast', 'F:/Medical QC SYS/datasets/chest_ct_non_contrast_qc/normalized/subset0_case_000.nii.gz', NULL, NOW(), NOW()),
  ('CHEST-NC-UID-002', 'CHESTNC002', '胸部平扫样例2', '女', 63, 'CHESTNC_PACS_002', '2026-03-14', '10:05:00', 'CT胸部平扫', 'CT', 1, 300, 'CHEST', 'LUNA16 Demo', 'Chest CT Non Contrast', 'F:/Medical QC SYS/datasets/chest_ct_non_contrast_qc/normalized/subset0_case_001.nii.gz', NULL, NOW(), NOW())
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
