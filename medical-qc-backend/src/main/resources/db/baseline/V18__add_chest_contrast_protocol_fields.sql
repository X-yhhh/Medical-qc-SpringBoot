-- Flyway V18
-- 目标：
-- 1. 为胸部增强本地患者缓存表和 PACS 缓存表补充协议参数字段
-- 2. 便于 PACS 演示样本直接携带流速、总量、注射部位、层厚与追踪阈值

SET @stmt = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chest_contrast_patient_info' AND COLUMN_NAME = 'flow_rate') = 0,
  'ALTER TABLE `chest_contrast_patient_info` ADD COLUMN `flow_rate` decimal(5,2) DEFAULT NULL',
  'SELECT 1'
);
PREPARE sql_stmt FROM @stmt;
EXECUTE sql_stmt;
DEALLOCATE PREPARE sql_stmt;

SET @stmt = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chest_contrast_patient_info' AND COLUMN_NAME = 'contrast_volume') = 0,
  'ALTER TABLE `chest_contrast_patient_info` ADD COLUMN `contrast_volume` int DEFAULT NULL',
  'SELECT 1'
);
PREPARE sql_stmt FROM @stmt;
EXECUTE sql_stmt;
DEALLOCATE PREPARE sql_stmt;

SET @stmt = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chest_contrast_patient_info' AND COLUMN_NAME = 'injection_site') = 0,
  'ALTER TABLE `chest_contrast_patient_info` ADD COLUMN `injection_site` varchar(100) DEFAULT NULL',
  'SELECT 1'
);
PREPARE sql_stmt FROM @stmt;
EXECUTE sql_stmt;
DEALLOCATE PREPARE sql_stmt;

SET @stmt = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chest_contrast_patient_info' AND COLUMN_NAME = 'slice_thickness') = 0,
  'ALTER TABLE `chest_contrast_patient_info` ADD COLUMN `slice_thickness` decimal(5,2) DEFAULT NULL',
  'SELECT 1'
);
PREPARE sql_stmt FROM @stmt;
EXECUTE sql_stmt;
DEALLOCATE PREPARE sql_stmt;

SET @stmt = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chest_contrast_patient_info' AND COLUMN_NAME = 'bolus_tracking_hu') = 0,
  'ALTER TABLE `chest_contrast_patient_info` ADD COLUMN `bolus_tracking_hu` int DEFAULT NULL',
  'SELECT 1'
);
PREPARE sql_stmt FROM @stmt;
EXECUTE sql_stmt;
DEALLOCATE PREPARE sql_stmt;

SET @stmt = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chest_contrast_patient_info' AND COLUMN_NAME = 'scan_delay_sec') = 0,
  'ALTER TABLE `chest_contrast_patient_info` ADD COLUMN `scan_delay_sec` int DEFAULT NULL',
  'SELECT 1'
);
PREPARE sql_stmt FROM @stmt;
EXECUTE sql_stmt;
DEALLOCATE PREPARE sql_stmt;

SET @stmt = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chest_contrast_pacs_study_cache' AND COLUMN_NAME = 'flow_rate') = 0,
  'ALTER TABLE `chest_contrast_pacs_study_cache` ADD COLUMN `flow_rate` decimal(5,2) DEFAULT NULL',
  'SELECT 1'
);
PREPARE sql_stmt FROM @stmt;
EXECUTE sql_stmt;
DEALLOCATE PREPARE sql_stmt;

SET @stmt = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chest_contrast_pacs_study_cache' AND COLUMN_NAME = 'contrast_volume') = 0,
  'ALTER TABLE `chest_contrast_pacs_study_cache` ADD COLUMN `contrast_volume` int DEFAULT NULL',
  'SELECT 1'
);
PREPARE sql_stmt FROM @stmt;
EXECUTE sql_stmt;
DEALLOCATE PREPARE sql_stmt;

SET @stmt = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chest_contrast_pacs_study_cache' AND COLUMN_NAME = 'injection_site') = 0,
  'ALTER TABLE `chest_contrast_pacs_study_cache` ADD COLUMN `injection_site` varchar(100) DEFAULT NULL',
  'SELECT 1'
);
PREPARE sql_stmt FROM @stmt;
EXECUTE sql_stmt;
DEALLOCATE PREPARE sql_stmt;

SET @stmt = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chest_contrast_pacs_study_cache' AND COLUMN_NAME = 'slice_thickness') = 0,
  'ALTER TABLE `chest_contrast_pacs_study_cache` ADD COLUMN `slice_thickness` decimal(5,2) DEFAULT NULL',
  'SELECT 1'
);
PREPARE sql_stmt FROM @stmt;
EXECUTE sql_stmt;
DEALLOCATE PREPARE sql_stmt;

SET @stmt = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chest_contrast_pacs_study_cache' AND COLUMN_NAME = 'bolus_tracking_hu') = 0,
  'ALTER TABLE `chest_contrast_pacs_study_cache` ADD COLUMN `bolus_tracking_hu` int DEFAULT NULL',
  'SELECT 1'
);
PREPARE sql_stmt FROM @stmt;
EXECUTE sql_stmt;
DEALLOCATE PREPARE sql_stmt;

SET @stmt = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chest_contrast_pacs_study_cache' AND COLUMN_NAME = 'scan_delay_sec') = 0,
  'ALTER TABLE `chest_contrast_pacs_study_cache` ADD COLUMN `scan_delay_sec` int DEFAULT NULL',
  'SELECT 1'
);
PREPARE sql_stmt FROM @stmt;
EXECUTE sql_stmt;
DEALLOCATE PREPARE sql_stmt;
