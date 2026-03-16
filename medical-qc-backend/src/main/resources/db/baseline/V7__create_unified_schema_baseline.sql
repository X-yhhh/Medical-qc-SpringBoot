-- Flyway V7
-- 目标：
-- 1. 提供不依赖旧库的统一模型基线
-- 2. 新环境仅执行本脚本即可完成建库
-- 3. 已迁移环境重复执行保持幂等

CREATE TABLE IF NOT EXISTS `user_roles` (
  `id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(20) NOT NULL,
  `description` varchar(100) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_roles_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `user_roles` (`id`, `name`, `description`)
VALUES
  (1, 'admin', '系统管理员'),
  (2, 'doctor', '医生')
ON DUPLICATE KEY UPDATE
  `description` = VALUES(`description`);

CREATE TABLE IF NOT EXISTS `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL,
  `email` varchar(100) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `full_name` varchar(100) DEFAULT NULL,
  `hospital` varchar(100) DEFAULT NULL,
  `department` varchar(50) DEFAULT NULL,
  `role_id` int NOT NULL DEFAULT 2,
  `is_active` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `access_token` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_username_role` (`username`, `role_id`),
  UNIQUE KEY `uk_users_email` (`email`),
  UNIQUE KEY `uk_users_access_token` (`access_token`),
  KEY `idx_users_role_id` (`role_id`),
  CONSTRAINT `users_ibfk_1` FOREIGN KEY (`role_id`) REFERENCES `user_roles` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `patients` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `patient_no` varchar(64) NOT NULL,
  `patient_name` varchar(100) NOT NULL,
  `gender` varchar(20) DEFAULT NULL,
  `birth_date` date DEFAULT NULL,
  `age_text` varchar(32) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `remark` varchar(255) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_patients_patient_no` (`patient_no`),
  KEY `idx_patients_name` (`patient_name`),
  KEY `idx_patients_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一患者主数据表';

CREATE TABLE IF NOT EXISTS `studies` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `study_no` varchar(64) NOT NULL,
  `patient_id` bigint NOT NULL,
  `accession_number` varchar(100) NOT NULL,
  `study_instance_uid` varchar(128) DEFAULT NULL,
  `modality` varchar(20) DEFAULT NULL,
  `body_part` varchar(100) DEFAULT NULL,
  `study_date` date DEFAULT NULL,
  `study_time` time DEFAULT NULL,
  `study_description` varchar(255) DEFAULT NULL,
  `source_type` varchar(20) NOT NULL DEFAULT 'MANUAL',
  `source_ref` varchar(255) DEFAULT NULL,
  `manufacturer` varchar(100) DEFAULT NULL,
  `device_model` varchar(100) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_studies_study_no` (`study_no`),
  UNIQUE KEY `uk_studies_accession_number` (`accession_number`),
  KEY `idx_studies_patient_date` (`patient_id`, `study_date`),
  KEY `idx_studies_source_type` (`source_type`),
  CONSTRAINT `studies_ibfk_1` FOREIGN KEY (`patient_id`) REFERENCES `patients` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一检查实例表';

CREATE TABLE IF NOT EXISTS `study_files` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `study_id` bigint NOT NULL,
  `file_role` varchar(30) NOT NULL DEFAULT 'SOURCE',
  `storage_type` varchar(20) NOT NULL DEFAULT 'LOCAL',
  `file_path` varchar(500) NOT NULL,
  `public_path` varchar(500) DEFAULT NULL,
  `file_name` varchar(255) DEFAULT NULL,
  `content_type` varchar(100) DEFAULT NULL,
  `file_size` bigint DEFAULT NULL,
  `checksum` varchar(128) DEFAULT NULL,
  `is_primary` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_study_files_study_role` (`study_id`, `file_role`),
  KEY `idx_study_files_primary` (`study_id`, `is_primary`),
  CONSTRAINT `study_files_ibfk_1` FOREIGN KEY (`study_id`) REFERENCES `studies` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='检查文件与影像资源表';

CREATE TABLE IF NOT EXISTS `qc_task_types` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(50) NOT NULL,
  `name` varchar(100) NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT 1,
  `description` varchar(255) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_qc_task_types_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='质控任务类型字典表';

INSERT INTO `qc_task_types` (`code`, `name`, `enabled`, `description`)
VALUES
  ('hemorrhage', '头部出血检测', 1, '头部出血 AI 检测'),
  ('head', 'CT头部平扫质控', 1, 'CT头部平扫质控任务'),
  ('chest-non-contrast', 'CT胸部平扫质控', 1, 'CT胸部平扫质控任务'),
  ('chest-contrast', 'CT胸部增强质控', 1, 'CT胸部增强质控任务'),
  ('coronary-cta', '冠脉CTA质控', 1, '冠脉 CTA 质控任务')
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `enabled` = VALUES(`enabled`),
  `description` = VALUES(`description`);

CREATE TABLE IF NOT EXISTS `qc_tasks` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_no` varchar(64) NOT NULL,
  `task_type_code` varchar(50) NOT NULL,
  `study_id` bigint DEFAULT NULL,
  `submitted_by` bigint DEFAULT NULL,
  `source_mode` varchar(20) NOT NULL DEFAULT 'local',
  `task_status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `priority` varchar(20) DEFAULT NULL,
  `scheduler_type` varchar(20) NOT NULL DEFAULT 'SYNC',
  `is_mock` tinyint(1) NOT NULL DEFAULT 0,
  `requested_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `started_at` datetime DEFAULT NULL,
  `completed_at` datetime DEFAULT NULL,
  `error_code` varchar(50) DEFAULT NULL,
  `error_message` varchar(500) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_qc_tasks_task_no` (`task_no`),
  KEY `idx_qc_tasks_status_created` (`task_status`, `created_at`),
  KEY `idx_qc_tasks_study_created` (`study_id`, `created_at`),
  KEY `idx_qc_tasks_task_type_created` (`task_type_code`, `created_at`),
  CONSTRAINT `qc_tasks_ibfk_1` FOREIGN KEY (`study_id`) REFERENCES `studies` (`id`) ON DELETE SET NULL,
  CONSTRAINT `qc_tasks_ibfk_2` FOREIGN KEY (`submitted_by`) REFERENCES `users` (`id`) ON DELETE SET NULL,
  CONSTRAINT `qc_tasks_ibfk_3` FOREIGN KEY (`task_type_code`) REFERENCES `qc_task_types` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一质控任务表';

CREATE TABLE IF NOT EXISTS `qc_results` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` bigint NOT NULL,
  `result_version` int NOT NULL DEFAULT 1,
  `model_code` varchar(100) DEFAULT NULL,
  `model_version` varchar(100) DEFAULT NULL,
  `qc_status` varchar(20) DEFAULT NULL,
  `quality_score` decimal(5,1) DEFAULT NULL,
  `abnormal_count` int NOT NULL DEFAULT 0,
  `primary_issue_code` varchar(100) DEFAULT NULL,
  `primary_issue_name` varchar(100) DEFAULT NULL,
  `summary_json` json DEFAULT NULL,
  `raw_result_json` json DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_qc_results_task_version` (`task_id`, `result_version`),
  KEY `idx_qc_results_status_created` (`qc_status`, `created_at`),
  CONSTRAINT `qc_results_ibfk_1` FOREIGN KEY (`task_id`) REFERENCES `qc_tasks` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一质控结果摘要表';

CREATE TABLE IF NOT EXISTS `qc_result_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `result_id` bigint NOT NULL,
  `item_code` varchar(100) DEFAULT NULL,
  `item_name` varchar(100) NOT NULL,
  `item_status` varchar(20) DEFAULT NULL,
  `score` decimal(8,2) DEFAULT NULL,
  `threshold_value` varchar(100) DEFAULT NULL,
  `detail_text` varchar(500) DEFAULT NULL,
  `detail_json` json DEFAULT NULL,
  `sort_order` int NOT NULL DEFAULT 0,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_qc_result_items_result_sort` (`result_id`, `sort_order`),
  KEY `idx_qc_result_items_code` (`item_code`),
  CONSTRAINT `qc_result_items_ibfk_1` FOREIGN KEY (`result_id`) REFERENCES `qc_results` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一质控结果明细项表';

CREATE TABLE IF NOT EXISTS `issue_tickets` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ticket_no` varchar(64) NOT NULL,
  `result_id` bigint DEFAULT NULL,
  `task_id` bigint DEFAULT NULL,
  `patient_id` bigint DEFAULT NULL,
  `study_id` bigint DEFAULT NULL,
  `issue_code` varchar(100) DEFAULT NULL,
  `issue_name` varchar(100) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `priority` varchar(20) NOT NULL DEFAULT '中',
  `responsible_role` varchar(20) DEFAULT NULL,
  `assignee_user_id` bigint DEFAULT NULL,
  `sla_hours` int DEFAULT NULL,
  `due_at` datetime DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT '待处理',
  `last_remark` varchar(500) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `resolved_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_issue_tickets_ticket_no` (`ticket_no`),
  KEY `idx_issue_tickets_status_created` (`status`, `created_at`),
  KEY `idx_issue_tickets_assignee` (`assignee_user_id`),
  KEY `idx_issue_tickets_patient_study` (`patient_id`, `study_id`),
  CONSTRAINT `issue_tickets_ibfk_1` FOREIGN KEY (`result_id`) REFERENCES `qc_results` (`id`) ON DELETE SET NULL,
  CONSTRAINT `issue_tickets_ibfk_2` FOREIGN KEY (`task_id`) REFERENCES `qc_tasks` (`id`) ON DELETE SET NULL,
  CONSTRAINT `issue_tickets_ibfk_3` FOREIGN KEY (`patient_id`) REFERENCES `patients` (`id`) ON DELETE SET NULL,
  CONSTRAINT `issue_tickets_ibfk_4` FOREIGN KEY (`study_id`) REFERENCES `studies` (`id`) ON DELETE SET NULL,
  CONSTRAINT `issue_tickets_ibfk_5` FOREIGN KEY (`assignee_user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一异常工单表';

CREATE TABLE IF NOT EXISTS `issue_action_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ticket_id` bigint NOT NULL,
  `operator_id` bigint DEFAULT NULL,
  `action_type` varchar(50) NOT NULL,
  `before_status` varchar(20) DEFAULT NULL,
  `after_status` varchar(20) DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_issue_action_logs_ticket_created` (`ticket_id`, `created_at`),
  CONSTRAINT `issue_action_logs_ibfk_1` FOREIGN KEY (`ticket_id`) REFERENCES `issue_tickets` (`id`) ON DELETE CASCADE,
  CONSTRAINT `issue_action_logs_ibfk_2` FOREIGN KEY (`operator_id`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一异常工单动作日志表';

CREATE TABLE IF NOT EXISTS `issue_capa_records` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ticket_id` bigint NOT NULL,
  `root_cause_category` varchar(100) DEFAULT NULL,
  `root_cause_detail` varchar(500) DEFAULT NULL,
  `corrective_action` varchar(500) DEFAULT NULL,
  `preventive_action` varchar(500) DEFAULT NULL,
  `verification_note` varchar(500) DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_issue_capa_records_ticket_id` (`ticket_id`),
  CONSTRAINT `issue_capa_records_ibfk_1` FOREIGN KEY (`ticket_id`) REFERENCES `issue_tickets` (`id`) ON DELETE CASCADE,
  CONSTRAINT `issue_capa_records_ibfk_2` FOREIGN KEY (`updated_by`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一异常工单 CAPA 表';

CREATE TABLE IF NOT EXISTS `qc_rules` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_type` varchar(50) NOT NULL,
  `task_type_name` varchar(100) DEFAULT NULL,
  `issue_type` varchar(100) NOT NULL,
  `priority` varchar(20) NOT NULL DEFAULT '中',
  `responsible_role` varchar(20) NOT NULL DEFAULT 'doctor',
  `sla_hours` int NOT NULL DEFAULT 24,
  `auto_create_issue` tinyint(1) NOT NULL DEFAULT 1,
  `enabled` tinyint(1) NOT NULL DEFAULT 1,
  `description` varchar(255) DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_qc_rules_task_issue` (`task_type`, `issue_type`),
  KEY `idx_qc_rules_task_enabled` (`task_type`, `enabled`),
  CONSTRAINT `qc_rules_ibfk_1` FOREIGN KEY (`updated_by`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一质控规则表';
