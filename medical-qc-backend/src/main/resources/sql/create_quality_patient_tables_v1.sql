-- 五个质控项患者信息管理表。
-- 说明：
-- 1. 五张表字段保持一致，便于通过统一服务进行管理。
-- 2. 当前仅头部出血检测与 PACS 真正联动该患者主数据；其余四项先预留表结构与管理能力。

CREATE TABLE IF NOT EXISTS `head_patient_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `patient_id` varchar(100) DEFAULT NULL COMMENT '患者ID',
  `patient_name` varchar(100) NOT NULL COMMENT '患者姓名',
  `accession_number` varchar(100) NOT NULL COMMENT '检查号',
  `gender` varchar(20) DEFAULT NULL COMMENT '性别',
  `age` int DEFAULT NULL COMMENT '年龄',
  `study_date` date DEFAULT NULL COMMENT '检查日期',
  `image_path` varchar(500) DEFAULT NULL COMMENT '患者影像图片路径',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_head_patient_info_accession_number` (`accession_number`),
  KEY `idx_head_patient_info_patient_id` (`patient_id`),
  KEY `idx_head_patient_info_patient_name` (`patient_name`),
  KEY `idx_head_patient_info_study_date` (`study_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='CT头部平扫患者信息表';

CREATE TABLE IF NOT EXISTS `hemorrhage_patient_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `patient_id` varchar(100) DEFAULT NULL COMMENT '患者ID',
  `patient_name` varchar(100) NOT NULL COMMENT '患者姓名',
  `accession_number` varchar(100) NOT NULL COMMENT '检查号',
  `gender` varchar(20) DEFAULT NULL COMMENT '性别',
  `age` int DEFAULT NULL COMMENT '年龄',
  `study_date` date DEFAULT NULL COMMENT '检查日期',
  `image_path` varchar(500) DEFAULT NULL COMMENT '患者影像图片路径',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_hemorrhage_patient_info_accession_number` (`accession_number`),
  KEY `idx_hemorrhage_patient_info_patient_id` (`patient_id`),
  KEY `idx_hemorrhage_patient_info_patient_name` (`patient_name`),
  KEY `idx_hemorrhage_patient_info_study_date` (`study_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='头部出血检测患者信息表';

CREATE TABLE IF NOT EXISTS `chest_non_contrast_patient_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `patient_id` varchar(100) DEFAULT NULL COMMENT '患者ID',
  `patient_name` varchar(100) NOT NULL COMMENT '患者姓名',
  `accession_number` varchar(100) NOT NULL COMMENT '检查号',
  `gender` varchar(20) DEFAULT NULL COMMENT '性别',
  `age` int DEFAULT NULL COMMENT '年龄',
  `study_date` date DEFAULT NULL COMMENT '检查日期',
  `image_path` varchar(500) DEFAULT NULL COMMENT '患者影像图片路径',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chest_non_contrast_patient_info_accession_number` (`accession_number`),
  KEY `idx_chest_non_contrast_patient_info_patient_id` (`patient_id`),
  KEY `idx_chest_non_contrast_patient_info_patient_name` (`patient_name`),
  KEY `idx_chest_non_contrast_patient_info_study_date` (`study_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='CT胸部平扫患者信息表';

CREATE TABLE IF NOT EXISTS `chest_contrast_patient_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `patient_id` varchar(100) DEFAULT NULL COMMENT '患者ID',
  `patient_name` varchar(100) NOT NULL COMMENT '患者姓名',
  `accession_number` varchar(100) NOT NULL COMMENT '检查号',
  `gender` varchar(20) DEFAULT NULL COMMENT '性别',
  `age` int DEFAULT NULL COMMENT '年龄',
  `study_date` date DEFAULT NULL COMMENT '检查日期',
  `image_path` varchar(500) DEFAULT NULL COMMENT '患者影像图片路径',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chest_contrast_patient_info_accession_number` (`accession_number`),
  KEY `idx_chest_contrast_patient_info_patient_id` (`patient_id`),
  KEY `idx_chest_contrast_patient_info_patient_name` (`patient_name`),
  KEY `idx_chest_contrast_patient_info_study_date` (`study_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='CT胸部增强患者信息表';

CREATE TABLE IF NOT EXISTS `coronary_cta_patient_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `patient_id` varchar(100) DEFAULT NULL COMMENT '患者ID',
  `patient_name` varchar(100) NOT NULL COMMENT '患者姓名',
  `accession_number` varchar(100) NOT NULL COMMENT '检查号',
  `gender` varchar(20) DEFAULT NULL COMMENT '性别',
  `age` int DEFAULT NULL COMMENT '年龄',
  `study_date` date DEFAULT NULL COMMENT '检查日期',
  `image_path` varchar(500) DEFAULT NULL COMMENT '患者影像图片路径',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_coronary_cta_patient_info_accession_number` (`accession_number`),
  KEY `idx_coronary_cta_patient_info_patient_id` (`patient_id`),
  KEY `idx_coronary_cta_patient_info_patient_name` (`patient_name`),
  KEY `idx_coronary_cta_patient_info_study_date` (`study_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='冠脉CTA患者信息表';
