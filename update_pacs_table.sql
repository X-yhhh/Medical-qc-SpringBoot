-- 修改PACS表，添加患者详细信息和影像路径
USE medical_qc_sys;

-- 添加新字段
ALTER TABLE `pacs_study_cache`
ADD COLUMN `gender` varchar(20) DEFAULT NULL COMMENT '性别' AFTER `patient_name`,
ADD COLUMN `age` int DEFAULT NULL COMMENT '年龄' AFTER `gender`,
ADD COLUMN `image_file_path` varchar(500) DEFAULT NULL COMMENT '影像文件路径' AFTER `model_name`;

-- 更新现有数据，添加患者信息和影像路径
UPDATE `pacs_study_cache` SET
  `gender` = '男',
  `age` = 45,
  `image_file_path` = 'F:/Medical QC SYS/medical-qc-backend/python_model/data/head_ct/001.png'
WHERE `id` = 1;

UPDATE `pacs_study_cache` SET
  `gender` = '女',
  `age` = 38,
  `image_file_path` = 'F:/Medical QC SYS/medical-qc-backend/python_model/data/head_ct/002.png'
WHERE `id` = 2;

UPDATE `pacs_study_cache` SET
  `gender` = '男',
  `age` = 52,
  `image_file_path` = 'F:/Medical QC SYS/medical-qc-backend/python_model/data/head_ct/003.png'
WHERE `id` = 3;

UPDATE `pacs_study_cache` SET
  `gender` = '女',
  `age` = 41,
  `image_file_path` = 'F:/Medical QC SYS/medical-qc-backend/python_model/data/head_ct/004.png'
WHERE `id` = 4;

UPDATE `pacs_study_cache` SET
  `gender` = '男',
  `age` = 56,
  `image_file_path` = 'F:/Medical QC SYS/medical-qc-backend/python_model/data/head_ct/005.png'
WHERE `id` = 5;
