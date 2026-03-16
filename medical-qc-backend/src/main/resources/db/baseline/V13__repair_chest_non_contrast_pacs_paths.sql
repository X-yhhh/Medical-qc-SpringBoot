UPDATE `chest_non_contrast_pacs_study_cache`
SET
  `image_count` = 121,
  `image_file_path` = 'F:/Medical QC SYS/datasets/chest_ct_non_contrast_qc/normalized/1.3.6.1.4.1.14519.5.2.1.6279.6001.105756658031515062000744821260.nii.gz',
  `updated_at` = NOW()
WHERE `accession_number` = 'CHESTNC_PACS_001';

UPDATE `chest_non_contrast_pacs_study_cache`
SET
  `image_count` = 119,
  `image_file_path` = 'F:/Medical QC SYS/datasets/chest_ct_non_contrast_qc/normalized/1.3.6.1.4.1.14519.5.2.1.6279.6001.108197895896446896160048741492.nii.gz',
  `updated_at` = NOW()
WHERE `accession_number` = 'CHESTNC_PACS_002';
