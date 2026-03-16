-- Flyway V10
-- 兼容旧环境中已执行旧版 V8 的数据库结构。
-- 新环境已由 V8 直接创建最终表结构，这里只保留幂等的字段补齐。

ALTER TABLE `coronary_cta_patient_info`
  ADD COLUMN IF NOT EXISTS `heart_rate` int DEFAULT NULL,
  ADD COLUMN IF NOT EXISTS `hr_variability` int DEFAULT NULL,
  ADD COLUMN IF NOT EXISTS `recon_phase` varchar(100) DEFAULT NULL,
  ADD COLUMN IF NOT EXISTS `kvp` varchar(50) DEFAULT NULL;

ALTER TABLE `coronary_cta_pacs_study_cache`
  ADD COLUMN IF NOT EXISTS `heart_rate` int DEFAULT NULL,
  ADD COLUMN IF NOT EXISTS `hr_variability` int DEFAULT NULL,
  ADD COLUMN IF NOT EXISTS `recon_phase` varchar(100) DEFAULT NULL,
  ADD COLUMN IF NOT EXISTS `kvp` varchar(50) DEFAULT NULL;
