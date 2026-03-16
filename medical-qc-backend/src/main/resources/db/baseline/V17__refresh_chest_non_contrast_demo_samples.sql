-- Flyway V17
-- 目标：
-- 1. 胸部平扫 PACS 演示样本不再全部使用高分正常例
-- 2. 混入已验证可触发真实异常输出的样本，避免演示时默认全是 100 分

UPDATE `chest_non_contrast_pacs_study_cache`
SET
  `patient_name` = '胸部平扫样例1（正常）',
  `study_description` = 'CT胸部平扫 - 正常样例',
  `image_file_path` = 'F:/Medical QC SYS/datasets/chest_ct_non_contrast_qc/normalized/1.3.6.1.4.1.14519.5.2.1.6279.6001.105756658031515062000744821260.nii.gz',
  `updated_at` = NOW()
WHERE `accession_number` = 'CHESTNC_PACS_001';

UPDATE `chest_non_contrast_pacs_study_cache`
SET
  `patient_name` = '胸部平扫样例2（纵隔窗异常）',
  `study_description` = 'CT胸部平扫 - 异常样例',
  `image_file_path` = 'F:/Medical QC SYS/datasets/chest_ct_non_contrast_qc/normalized/1.3.6.1.4.1.14519.5.2.1.6279.6001.323302986710576400812869264321.nii.gz',
  `updated_at` = NOW()
WHERE `accession_number` = 'CHESTNC_PACS_002';
