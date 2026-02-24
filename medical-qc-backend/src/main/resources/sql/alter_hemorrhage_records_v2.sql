-- 扩展 hemorrhage_records 表结构：新增模型扩展字段与原始结果 JSON
-- 适用于已存在数据库的增量升级；执行前请确认当前库为 medical_qc_sys 且表存在。

ALTER TABLE `hemorrhage_records`
  ADD COLUMN `midline_shift` tinyint(1) DEFAULT NULL,
  ADD COLUMN `shift_score` float DEFAULT NULL,
  ADD COLUMN `midline_detail` varchar(255) DEFAULT NULL,
  ADD COLUMN `ventricle_issue` tinyint(1) DEFAULT NULL,
  ADD COLUMN `ventricle_detail` varchar(255) DEFAULT NULL,
  ADD COLUMN `device` varchar(100) DEFAULT NULL,
  ADD COLUMN `raw_result_json` longtext DEFAULT NULL,
  ADD COLUMN `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
