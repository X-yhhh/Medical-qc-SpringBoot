CREATE DATABASE IF NOT EXISTS medical_qc_sys;
USE medical_qc_sys;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for user_roles
-- ----------------------------
DROP TABLE IF EXISTS `user_roles`;
CREATE TABLE `user_roles` (
  `id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(20) NOT NULL,
  `description` varchar(100) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_roles_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of user_roles
-- ----------------------------
INSERT INTO `user_roles` (`id`, `name`, `description`) VALUES (1, 'admin', '系统管理员');
INSERT INTO `user_roles` (`id`, `name`, `description`) VALUES (2, 'doctor', '医生');

-- ----------------------------
-- Table structure for users
-- ----------------------------
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL,
  `email` varchar(100) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `full_name` varchar(100) DEFAULT NULL,
  `hospital` varchar(100) DEFAULT NULL,
  `department` varchar(50) DEFAULT NULL,
  `role_id` int NOT NULL DEFAULT '2',
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `access_token` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_username_role` (`username`, `role_id`),
  UNIQUE KEY `email` (`email`),
  UNIQUE KEY `access_token` (`access_token`),
  KEY `role_id` (`role_id`),
  CONSTRAINT `users_ibfk_1` FOREIGN KEY (`role_id`) REFERENCES `user_roles` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Table structure for hemorrhage_records
-- 说明：
-- 1. hemorrhage_records 为“头部出血检测”历史记录表。
-- 2. 其他质控项后续按相同模式分别建设独立历史表。
-- 3. 首页“影像质控工作台 / 最近访问”优先读取该类历史表中的实时数据。
-- ----------------------------
DROP TABLE IF EXISTS `hemorrhage_records`;
CREATE TABLE `hemorrhage_records` (
  `id` int NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `patient_name` varchar(100) DEFAULT NULL,
  `patient_code` varchar(100) DEFAULT NULL,
  `exam_id` varchar(100) DEFAULT NULL,
  `gender` varchar(20) DEFAULT NULL,
  `age` int DEFAULT NULL,
  `study_date` date DEFAULT NULL,
  `image_path` varchar(500) NOT NULL,
  `prediction` varchar(50) NOT NULL,
  `qc_status` varchar(20) NOT NULL COMMENT '质控结论：合格/不合格',
  `confidence_level` varchar(50) DEFAULT NULL,
  `hemorrhage_probability` float NOT NULL,
  `no_hemorrhage_probability` float NOT NULL,
  `analysis_duration` float DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `midline_shift` tinyint(1) DEFAULT NULL,
  `shift_score` float DEFAULT NULL,
  `midline_detail` varchar(255) DEFAULT NULL,
  `ventricle_issue` tinyint(1) DEFAULT NULL,
  `ventricle_detail` varchar(255) DEFAULT NULL,
  `device` varchar(100) DEFAULT NULL,
  `raw_result_json` longtext DEFAULT NULL,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  KEY `idx_hemorrhage_user_created_at` (`user_id`, `created_at`),
  CONSTRAINT `hemorrhage_records_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Table structure for head_qc_records
-- 说明：
-- 1. head_qc_records 为“CT头部平扫”历史记录表。
-- 2. 当前页面仍为 mock，但表结构已按现有页面字段预建。
-- ----------------------------
DROP TABLE IF EXISTS `head_qc_records`;
CREATE TABLE `head_qc_records` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `patient_name` varchar(100) DEFAULT NULL,
  `exam_id` varchar(100) DEFAULT NULL,
  `accession_number` varchar(100) DEFAULT NULL,
  `gender` varchar(20) DEFAULT NULL,
  `age` int DEFAULT NULL,
  `study_date` date DEFAULT NULL,
  `device` varchar(100) DEFAULT NULL,
  `image_path` varchar(500) DEFAULT NULL,
  `slice_count` int DEFAULT NULL,
  `slice_thickness` decimal(6,2) DEFAULT NULL,
  `quality_score` decimal(5,1) DEFAULT NULL COMMENT '质控评分',
  `qc_status` varchar(20) NOT NULL DEFAULT '合格' COMMENT '质控结论：合格/不合格',
  `abnormal_count` int NOT NULL DEFAULT 0 COMMENT '异常项数量',
  `primary_issue` varchar(100) DEFAULT NULL COMMENT '主异常项',
  `analysis_duration` float DEFAULT NULL,
  `qc_items_json` longtext DEFAULT NULL COMMENT '质控项明细 JSON',
  `raw_result_json` longtext DEFAULT NULL COMMENT '原始响应 JSON',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_head_qc_user_created_at` (`user_id`, `created_at`),
  KEY `idx_head_qc_user_status_created` (`user_id`, `qc_status`, `created_at`),
  CONSTRAINT `head_qc_records_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Table structure for chest_non_contrast_qc_records
-- 说明：
-- 1. chest_non_contrast_qc_records 为“CT胸部平扫”历史记录表。
-- ----------------------------
DROP TABLE IF EXISTS `chest_non_contrast_qc_records`;
CREATE TABLE `chest_non_contrast_qc_records` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `patient_name` varchar(100) DEFAULT NULL,
  `exam_id` varchar(100) DEFAULT NULL,
  `accession_number` varchar(100) DEFAULT NULL,
  `gender` varchar(20) DEFAULT NULL,
  `age` int DEFAULT NULL,
  `study_date` date DEFAULT NULL,
  `device` varchar(100) DEFAULT NULL,
  `image_path` varchar(500) DEFAULT NULL,
  `slice_count` int DEFAULT NULL,
  `slice_thickness` decimal(6,2) DEFAULT NULL,
  `quality_score` decimal(5,1) DEFAULT NULL COMMENT '质控评分',
  `qc_status` varchar(20) NOT NULL DEFAULT '合格' COMMENT '质控结论：合格/不合格',
  `abnormal_count` int NOT NULL DEFAULT 0 COMMENT '异常项数量',
  `primary_issue` varchar(100) DEFAULT NULL COMMENT '主异常项',
  `analysis_duration` float DEFAULT NULL,
  `qc_items_json` longtext DEFAULT NULL COMMENT '质控项明细 JSON',
  `raw_result_json` longtext DEFAULT NULL COMMENT '原始响应 JSON',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_chest_non_contrast_user_created_at` (`user_id`, `created_at`),
  KEY `idx_chest_non_contrast_user_status_created` (`user_id`, `qc_status`, `created_at`),
  CONSTRAINT `chest_non_contrast_qc_records_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Table structure for chest_contrast_qc_records
-- 说明：
-- 1. chest_contrast_qc_records 为“CT胸部增强”历史记录表。
-- ----------------------------
DROP TABLE IF EXISTS `chest_contrast_qc_records`;
CREATE TABLE `chest_contrast_qc_records` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `patient_name` varchar(100) DEFAULT NULL,
  `exam_id` varchar(100) DEFAULT NULL,
  `accession_number` varchar(100) DEFAULT NULL,
  `gender` varchar(20) DEFAULT NULL,
  `age` int DEFAULT NULL,
  `study_date` date DEFAULT NULL,
  `device` varchar(100) DEFAULT NULL,
  `image_path` varchar(500) DEFAULT NULL,
  `slice_count` int DEFAULT NULL,
  `slice_thickness` decimal(6,2) DEFAULT NULL,
  `flow_rate` decimal(6,2) DEFAULT NULL COMMENT '造影剂流速 mL/s',
  `contrast_volume` decimal(8,2) DEFAULT NULL COMMENT '造影剂总量 mL',
  `injection_site` varchar(100) DEFAULT NULL COMMENT '注射部位',
  `quality_score` decimal(5,1) DEFAULT NULL COMMENT '质控评分',
  `qc_status` varchar(20) NOT NULL DEFAULT '合格' COMMENT '质控结论：合格/不合格',
  `abnormal_count` int NOT NULL DEFAULT 0 COMMENT '异常项数量',
  `primary_issue` varchar(100) DEFAULT NULL COMMENT '主异常项',
  `analysis_duration` float DEFAULT NULL,
  `qc_items_json` longtext DEFAULT NULL COMMENT '质控项明细 JSON',
  `raw_result_json` longtext DEFAULT NULL COMMENT '原始响应 JSON',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_chest_contrast_user_created_at` (`user_id`, `created_at`),
  KEY `idx_chest_contrast_user_status_created` (`user_id`, `qc_status`, `created_at`),
  CONSTRAINT `chest_contrast_qc_records_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Table structure for coronary_cta_qc_records
-- 说明：
-- 1. coronary_cta_qc_records 为“冠脉CTA”历史记录表。
-- ----------------------------
DROP TABLE IF EXISTS `coronary_cta_qc_records`;
CREATE TABLE `coronary_cta_qc_records` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `patient_name` varchar(100) DEFAULT NULL,
  `exam_id` varchar(100) DEFAULT NULL,
  `accession_number` varchar(100) DEFAULT NULL,
  `gender` varchar(20) DEFAULT NULL,
  `age` int DEFAULT NULL,
  `study_date` date DEFAULT NULL,
  `device` varchar(100) DEFAULT NULL,
  `image_path` varchar(500) DEFAULT NULL,
  `slice_count` int DEFAULT NULL,
  `slice_thickness` decimal(6,2) DEFAULT NULL,
  `heart_rate` int DEFAULT NULL COMMENT '平均心率 bpm',
  `hr_variability` int DEFAULT NULL COMMENT '心率波动 bpm',
  `recon_phase` varchar(100) DEFAULT NULL COMMENT '重建相位',
  `kvp` varchar(50) DEFAULT NULL COMMENT '管电压',
  `quality_score` decimal(5,1) DEFAULT NULL COMMENT '质控评分',
  `qc_status` varchar(20) NOT NULL DEFAULT '合格' COMMENT '质控结论：合格/不合格',
  `abnormal_count` int NOT NULL DEFAULT 0 COMMENT '异常项数量',
  `primary_issue` varchar(100) DEFAULT NULL COMMENT '主异常项',
  `analysis_duration` float DEFAULT NULL,
  `qc_items_json` longtext DEFAULT NULL COMMENT '质控项明细 JSON',
  `raw_result_json` longtext DEFAULT NULL COMMENT '原始响应 JSON',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_coronary_cta_user_created_at` (`user_id`, `created_at`),
  KEY `idx_coronary_cta_user_status_created` (`user_id`, `qc_status`, `created_at`),
  CONSTRAINT `coronary_cta_qc_records_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Table structure for qc_issue_records
-- 说明：
-- 1. qc_issue_records 为统一异常工单表。
-- 2. 当前先承接脑出血检测生成的异常工单，后续其他质控项按 source_type 接入。
-- 3. 首页待办/风险预警、异常汇总页状态流转均以该表为主。
-- ----------------------------
DROP TABLE IF EXISTS `qc_issue_records`;
CREATE TABLE `qc_issue_records` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `source_type` varchar(50) NOT NULL COMMENT '来源模块，如 hemorrhage',
  `source_record_id` bigint NOT NULL COMMENT '来源历史记录 ID',
  `patient_name` varchar(100) DEFAULT NULL,
  `exam_id` varchar(100) DEFAULT NULL,
  `issue_type` varchar(100) NOT NULL COMMENT '主异常项',
  `description` varchar(255) NOT NULL COMMENT '异常描述',
  `priority` varchar(20) NOT NULL DEFAULT '低' COMMENT '优先级：高/中/低',
  `status` varchar(20) NOT NULL DEFAULT '待处理' COMMENT '状态：待处理/处理中/已解决',
  `image_url` varchar(500) DEFAULT NULL,
  `last_remark` varchar(500) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `resolved_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_qc_issue_source` (`user_id`, `source_type`, `source_record_id`),
  KEY `idx_qc_issue_user_status_created` (`user_id`, `status`, `created_at`),
  CONSTRAINT `qc_issue_records_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Table structure for qc_issue_handle_logs
-- 说明：
-- 1. qc_issue_handle_logs 为异常工单处理日志表。
-- 2. 记录工单状态变化、处理备注与操作人。
-- ----------------------------
DROP TABLE IF EXISTS `qc_issue_handle_logs`;
CREATE TABLE `qc_issue_handle_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `issue_id` bigint NOT NULL,
  `operator_id` bigint NOT NULL,
  `action_type` varchar(50) NOT NULL COMMENT '动作类型：create/update_status',
  `before_status` varchar(20) DEFAULT NULL,
  `after_status` varchar(20) DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_issue_handle_logs_issue_created` (`issue_id`, `created_at`),
  CONSTRAINT `qc_issue_handle_logs_ibfk_1` FOREIGN KEY (`issue_id`) REFERENCES `qc_issue_records` (`id`) ON DELETE CASCADE,
  CONSTRAINT `qc_issue_handle_logs_ibfk_2` FOREIGN KEY (`operator_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET FOREIGN_KEY_CHECKS = 1;
