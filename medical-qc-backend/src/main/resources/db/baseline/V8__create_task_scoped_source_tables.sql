-- Flyway V8
-- 目标：
-- 1. 为每个质控项创建独立的患者缓存表与 PACS 缓存表
-- 2. 不再依赖通用 pacs_study_cache 中转表
-- 3. 保持 mock 质控项也具备独立的任务专属源数据骨架

CREATE TABLE IF NOT EXISTS `head_patient_info` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `patient_id` varchar(64) DEFAULT NULL,
  `patient_name` varchar(100) NOT NULL,
  `accession_number` varchar(64) NOT NULL,
  `gender` varchar(20) DEFAULT NULL,
  `age` int DEFAULT NULL,
  `study_date` date DEFAULT NULL,
  `image_path` varchar(500) DEFAULT NULL,
  `remark` varchar(255) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_head_patient_info_accession` (`accession_number`),
  KEY `idx_head_patient_info_name` (`patient_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='CT头部平扫本地上传患者缓存表';

CREATE TABLE IF NOT EXISTS `hemorrhage_patient_info` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `patient_id` varchar(64) DEFAULT NULL,
  `patient_name` varchar(100) NOT NULL,
  `accession_number` varchar(64) NOT NULL,
  `gender` varchar(20) DEFAULT NULL,
  `age` int DEFAULT NULL,
  `study_date` date DEFAULT NULL,
  `image_path` varchar(500) DEFAULT NULL,
  `remark` varchar(255) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_hemorrhage_patient_info_accession` (`accession_number`),
  KEY `idx_hemorrhage_patient_info_name` (`patient_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='头部出血检测本地上传患者缓存表';

CREATE TABLE IF NOT EXISTS `chest_non_contrast_patient_info` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `patient_id` varchar(64) DEFAULT NULL,
  `patient_name` varchar(100) NOT NULL,
  `accession_number` varchar(64) NOT NULL,
  `gender` varchar(20) DEFAULT NULL,
  `age` int DEFAULT NULL,
  `study_date` date DEFAULT NULL,
  `image_path` varchar(500) DEFAULT NULL,
  `remark` varchar(255) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chest_nc_patient_info_accession` (`accession_number`),
  KEY `idx_chest_nc_patient_info_name` (`patient_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='CT胸部平扫本地上传患者缓存表';

CREATE TABLE IF NOT EXISTS `chest_contrast_patient_info` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `patient_id` varchar(64) DEFAULT NULL,
  `patient_name` varchar(100) NOT NULL,
  `accession_number` varchar(64) NOT NULL,
  `gender` varchar(20) DEFAULT NULL,
  `age` int DEFAULT NULL,
  `study_date` date DEFAULT NULL,
  `image_path` varchar(500) DEFAULT NULL,
  `remark` varchar(255) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chest_c_patient_info_accession` (`accession_number`),
  KEY `idx_chest_c_patient_info_name` (`patient_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='CT胸部增强本地上传患者缓存表';

CREATE TABLE IF NOT EXISTS `coronary_cta_patient_info` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `patient_id` varchar(64) DEFAULT NULL,
  `patient_name` varchar(100) NOT NULL,
  `accession_number` varchar(64) NOT NULL,
  `gender` varchar(20) DEFAULT NULL,
  `age` int DEFAULT NULL,
  `study_date` date DEFAULT NULL,
  `image_path` varchar(500) DEFAULT NULL,
  `remark` varchar(255) DEFAULT NULL,
  `heart_rate` int DEFAULT NULL,
  `hr_variability` int DEFAULT NULL,
  `recon_phase` varchar(100) DEFAULT NULL,
  `kvp` varchar(50) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_coronary_cta_patient_info_accession` (`accession_number`),
  KEY `idx_coronary_cta_patient_info_name` (`patient_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='冠脉CTA本地上传患者缓存表';

CREATE TABLE IF NOT EXISTS `head_pacs_study_cache` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `study_instance_uid` varchar(128) DEFAULT NULL,
  `patient_id` varchar(64) DEFAULT NULL,
  `patient_name` varchar(100) DEFAULT NULL,
  `gender` varchar(20) DEFAULT NULL,
  `age` int DEFAULT NULL,
  `accession_number` varchar(64) NOT NULL,
  `study_date` date DEFAULT NULL,
  `study_time` time DEFAULT NULL,
  `study_description` varchar(255) DEFAULT NULL,
  `modality` varchar(20) DEFAULT NULL,
  `series_count` int DEFAULT NULL,
  `image_count` int DEFAULT NULL,
  `body_part` varchar(100) DEFAULT NULL,
  `manufacturer` varchar(100) DEFAULT NULL,
  `model_name` varchar(100) DEFAULT NULL,
  `image_file_path` varchar(500) DEFAULT NULL,
  `patient_image_path` varchar(500) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_head_pacs_accession` (`accession_number`),
  KEY `idx_head_pacs_patient` (`patient_id`, `study_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='CT头部平扫 PACS 缓存表';

CREATE TABLE IF NOT EXISTS `hemorrhage_pacs_study_cache` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `study_instance_uid` varchar(128) DEFAULT NULL,
  `patient_id` varchar(64) DEFAULT NULL,
  `patient_name` varchar(100) DEFAULT NULL,
  `gender` varchar(20) DEFAULT NULL,
  `age` int DEFAULT NULL,
  `accession_number` varchar(64) NOT NULL,
  `study_date` date DEFAULT NULL,
  `study_time` time DEFAULT NULL,
  `study_description` varchar(255) DEFAULT NULL,
  `modality` varchar(20) DEFAULT NULL,
  `series_count` int DEFAULT NULL,
  `image_count` int DEFAULT NULL,
  `body_part` varchar(100) DEFAULT NULL,
  `manufacturer` varchar(100) DEFAULT NULL,
  `model_name` varchar(100) DEFAULT NULL,
  `image_file_path` varchar(500) DEFAULT NULL,
  `patient_image_path` varchar(500) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_hemorrhage_pacs_accession` (`accession_number`),
  KEY `idx_hemorrhage_pacs_patient` (`patient_id`, `study_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='头部出血检测 PACS 缓存表';

CREATE TABLE IF NOT EXISTS `chest_non_contrast_pacs_study_cache` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `study_instance_uid` varchar(128) DEFAULT NULL,
  `patient_id` varchar(64) DEFAULT NULL,
  `patient_name` varchar(100) DEFAULT NULL,
  `gender` varchar(20) DEFAULT NULL,
  `age` int DEFAULT NULL,
  `accession_number` varchar(64) NOT NULL,
  `study_date` date DEFAULT NULL,
  `study_time` time DEFAULT NULL,
  `study_description` varchar(255) DEFAULT NULL,
  `modality` varchar(20) DEFAULT NULL,
  `series_count` int DEFAULT NULL,
  `image_count` int DEFAULT NULL,
  `body_part` varchar(100) DEFAULT NULL,
  `manufacturer` varchar(100) DEFAULT NULL,
  `model_name` varchar(100) DEFAULT NULL,
  `image_file_path` varchar(500) DEFAULT NULL,
  `patient_image_path` varchar(500) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chest_nc_pacs_accession` (`accession_number`),
  KEY `idx_chest_nc_pacs_patient` (`patient_id`, `study_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='CT胸部平扫 PACS 缓存表';

CREATE TABLE IF NOT EXISTS `chest_contrast_pacs_study_cache` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `study_instance_uid` varchar(128) DEFAULT NULL,
  `patient_id` varchar(64) DEFAULT NULL,
  `patient_name` varchar(100) DEFAULT NULL,
  `gender` varchar(20) DEFAULT NULL,
  `age` int DEFAULT NULL,
  `accession_number` varchar(64) NOT NULL,
  `study_date` date DEFAULT NULL,
  `study_time` time DEFAULT NULL,
  `study_description` varchar(255) DEFAULT NULL,
  `modality` varchar(20) DEFAULT NULL,
  `series_count` int DEFAULT NULL,
  `image_count` int DEFAULT NULL,
  `body_part` varchar(100) DEFAULT NULL,
  `manufacturer` varchar(100) DEFAULT NULL,
  `model_name` varchar(100) DEFAULT NULL,
  `image_file_path` varchar(500) DEFAULT NULL,
  `patient_image_path` varchar(500) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chest_c_pacs_accession` (`accession_number`),
  KEY `idx_chest_c_pacs_patient` (`patient_id`, `study_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='CT胸部增强 PACS 缓存表';

CREATE TABLE IF NOT EXISTS `coronary_cta_pacs_study_cache` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `study_instance_uid` varchar(128) DEFAULT NULL,
  `patient_id` varchar(64) DEFAULT NULL,
  `patient_name` varchar(100) DEFAULT NULL,
  `gender` varchar(20) DEFAULT NULL,
  `age` int DEFAULT NULL,
  `accession_number` varchar(64) NOT NULL,
  `study_date` date DEFAULT NULL,
  `study_time` time DEFAULT NULL,
  `study_description` varchar(255) DEFAULT NULL,
  `modality` varchar(20) DEFAULT NULL,
  `series_count` int DEFAULT NULL,
  `image_count` int DEFAULT NULL,
  `body_part` varchar(100) DEFAULT NULL,
  `manufacturer` varchar(100) DEFAULT NULL,
  `model_name` varchar(100) DEFAULT NULL,
  `image_file_path` varchar(500) DEFAULT NULL,
  `patient_image_path` varchar(500) DEFAULT NULL,
  `heart_rate` int DEFAULT NULL,
  `hr_variability` int DEFAULT NULL,
  `recon_phase` varchar(100) DEFAULT NULL,
  `kvp` varchar(50) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_coronary_cta_pacs_accession` (`accession_number`),
  KEY `idx_coronary_cta_pacs_patient` (`patient_id`, `study_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='冠脉CTA PACS 缓存表';
