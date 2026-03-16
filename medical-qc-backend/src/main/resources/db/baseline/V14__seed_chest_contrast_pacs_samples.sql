INSERT INTO `chest_contrast_pacs_study_cache` (
  `study_instance_uid`, `patient_id`, `patient_name`, `gender`, `age`, `accession_number`, `study_date`, `study_time`,
  `study_description`, `modality`, `series_count`, `image_count`, `body_part`, `manufacturer`, `model_name`,
  `image_file_path`, `patient_image_path`, `created_at`, `updated_at`
)
VALUES
  ('CHEST-C-UID-001', 'CHESTC001', '胸部增强样例1', '男', 56, 'CHESTC_PACS_001', '2026-03-14', '14:00:00', 'CT胸部增强', 'CT', 2, 121, 'CHEST', 'Mock Demo', 'Chest Contrast Mock', 'F:/Medical QC SYS/uploads/mock/chest-contrast/chest_contrast_pacs_001.dcm', NULL, NOW(), NOW()),
  ('CHEST-C-UID-002', 'CHESTC002', '胸部增强样例2', '女', 62, 'CHESTC_PACS_002', '2026-03-14', '14:05:00', 'CT胸部增强', 'CT', 2, 119, 'CHEST', 'Mock Demo', 'Chest Contrast Mock', 'F:/Medical QC SYS/uploads/mock/chest-contrast/chest_contrast_pacs_002.dcm', NULL, NOW(), NOW())
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
