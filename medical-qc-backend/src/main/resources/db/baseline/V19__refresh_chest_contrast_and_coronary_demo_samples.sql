-- Flyway V19
-- 目标：
-- 1. 胸部增强与冠脉 CTA 演示样本使用可访问的 NIfTI 演示影像
-- 2. 为胸部增强 PACS 演示样本补入一组“正常协议”和一组“异常协议”
-- 3. 为冠脉 CTA PACS 演示样本补入一组“正常协议”和一组“异常协议”

UPDATE `chest_contrast_pacs_study_cache`
SET
  `patient_name` = '胸部增强样例1（协议良好）',
  `study_description` = 'CT胸部增强 - 正常协议样例',
  `image_file_path` = 'F:/Medical QC SYS/datasets/chest_ct_non_contrast_qc/normalized/1.3.6.1.4.1.14519.5.2.1.6279.6001.105756658031515062000744821260.nii.gz',
  `manufacturer` = 'Demo Dataset',
  `model_name` = 'Chest Contrast Demo',
  `flow_rate` = 4.50,
  `contrast_volume` = 80,
  `injection_site` = '右侧肘正中静脉',
  `slice_thickness` = 1.00,
  `bolus_tracking_hu` = 260,
  `scan_delay_sec` = 55,
  `updated_at` = NOW()
WHERE `accession_number` = 'CHESTC_PACS_001';

UPDATE `chest_contrast_pacs_study_cache`
SET
  `patient_name` = '胸部增强样例2（协议异常）',
  `study_description` = 'CT胸部增强 - 异常协议样例',
  `image_file_path` = 'F:/Medical QC SYS/datasets/chest_ct_non_contrast_qc/normalized/1.3.6.1.4.1.14519.5.2.1.6279.6001.323302986710576400812869264321.nii.gz',
  `manufacturer` = 'Demo Dataset',
  `model_name` = 'Chest Contrast Demo',
  `flow_rate` = 2.00,
  `contrast_volume` = 40,
  `injection_site` = '手背静脉',
  `slice_thickness` = 3.50,
  `bolus_tracking_hu` = 180,
  `scan_delay_sec` = 8,
  `updated_at` = NOW()
WHERE `accession_number` = 'CHESTC_PACS_002';

UPDATE `coronary_cta_pacs_study_cache`
SET
  `patient_name` = '冠脉CTA样例1（协议良好）',
  `study_description` = '冠脉CTA - 正常协议样例',
  `image_file_path` = 'F:/Medical QC SYS/datasets/chest_ct_non_contrast_qc/normalized/1.3.6.1.4.1.14519.5.2.1.6279.6001.105756658031515062000744821260.nii.gz',
  `manufacturer` = 'Demo Dataset',
  `model_name` = 'Coronary CTA Demo',
  `heart_rate` = 64,
  `hr_variability` = 2,
  `recon_phase` = '75%',
  `kvp` = '100 kV',
  `updated_at` = NOW()
WHERE `accession_number` = 'CTA_PACS_001';

UPDATE `coronary_cta_pacs_study_cache`
SET
  `patient_name` = '冠脉CTA样例2（协议异常）',
  `study_description` = '冠脉CTA - 异常协议样例',
  `image_file_path` = 'F:/Medical QC SYS/datasets/chest_ct_non_contrast_qc/normalized/1.3.6.1.4.1.14519.5.2.1.6279.6001.323302986710576400812869264321.nii.gz',
  `manufacturer` = 'Demo Dataset',
  `model_name` = 'Coronary CTA Demo',
  `heart_rate` = 88,
  `hr_variability` = 9,
  `recon_phase` = NULL,
  `kvp` = '80 kV',
  `updated_at` = NOW()
WHERE `accession_number` = 'CTA_PACS_002';
