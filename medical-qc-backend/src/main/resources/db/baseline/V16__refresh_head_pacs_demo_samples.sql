-- Flyway V16
-- 目标：
-- 1. 头部平扫 PACS 演示样本不再全部使用正常例
-- 2. 混入已验证可触发真实异常输出的样本，避免演示时所有案例都返回 100 分

UPDATE `head_pacs_study_cache`
SET
  `patient_name` = '头平扫样例1（正常）',
  `study_description` = 'CT头部平扫 - 正常样例',
  `image_file_path` = 'F:/Medical QC SYS/datasets/head_ct_plain_qc/raw/ct/0.nii.gz',
  `updated_at` = NOW()
WHERE `accession_number` = 'HEADQC_PACS_001';

UPDATE `head_pacs_study_cache`
SET
  `patient_name` = '头平扫样例2（正常）',
  `study_description` = 'CT头部平扫 - 正常样例',
  `image_file_path` = 'F:/Medical QC SYS/datasets/head_ct_plain_qc/raw/ct/1.nii.gz',
  `updated_at` = NOW()
WHERE `accession_number` = 'HEADQC_PACS_002';

UPDATE `head_pacs_study_cache`
SET
  `patient_name` = '头平扫样例3（正常）',
  `study_description` = 'CT头部平扫 - 正常样例',
  `image_file_path` = 'F:/Medical QC SYS/datasets/head_ct_plain_qc/raw/ct/2.nii.gz',
  `updated_at` = NOW()
WHERE `accession_number` = 'HEADQC_PACS_003';

UPDATE `head_pacs_study_cache`
SET
  `patient_name` = '头平扫样例4（运动伪影）',
  `study_description` = 'CT头部平扫 - 运动伪影样例',
  `image_file_path` = 'F:/Medical QC SYS/datasets/head_ct_plain_qc/raw/cbct/101.nii.gz',
  `updated_at` = NOW()
WHERE `accession_number` = 'HEADQC_PACS_004';

UPDATE `head_pacs_study_cache`
SET
  `patient_name` = '头平扫样例5（体位不正+运动伪影）',
  `study_description` = 'CT头部平扫 - 复合异常样例',
  `image_file_path` = 'F:/Medical QC SYS/datasets/head_ct_plain_qc/raw/cbct/103.nii.gz',
  `updated_at` = NOW()
WHERE `accession_number` = 'HEADQC_PACS_005';
