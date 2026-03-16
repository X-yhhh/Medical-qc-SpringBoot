INSERT INTO `coronary_cta_pacs_study_cache` (
  `study_instance_uid`, `patient_id`, `patient_name`, `gender`, `age`, `accession_number`, `study_date`, `study_time`,
  `study_description`, `modality`, `series_count`, `image_count`, `body_part`, `manufacturer`, `model_name`,
  `image_file_path`, `patient_image_path`, `heart_rate`, `hr_variability`, `recon_phase`, `kvp`, `created_at`, `updated_at`
)
VALUES
  ('CTA-UID-001', 'CTA001', '冠脉CTA样例1', '男', 61, 'CTA_PACS_001', '2026-03-14', '11:00:00', '冠脉CTA', 'CTA', 1, 320, 'CORONARY', 'Mock Demo', 'Coronary CTA Mock', 'F:/Medical QC SYS/uploads/mock/coronary-cta/cta_pacs_001.nii.gz', NULL, 68, 3, '75% (Diastolic)', '100 kV', NOW(), NOW()),
  ('CTA-UID-002', 'CTA002', '冠脉CTA样例2', '女', 57, 'CTA_PACS_002', '2026-03-14', '11:05:00', '冠脉CTA', 'CTA', 1, 320, 'CORONARY', 'Mock Demo', 'Coronary CTA Mock', 'F:/Medical QC SYS/uploads/mock/coronary-cta/cta_pacs_002.nii.gz', NULL, 72, 4, '70% (Diastolic)', '100 kV', NOW(), NOW())
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
  `heart_rate` = VALUES(`heart_rate`),
  `hr_variability` = VALUES(`hr_variability`),
  `recon_phase` = VALUES(`recon_phase`),
  `kvp` = VALUES(`kvp`),
  `updated_at` = NOW();
