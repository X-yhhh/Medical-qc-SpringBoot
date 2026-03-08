-- 统一异常工单表与处理日志表。
-- 当前先用于脑出血检测模块，后续其他质控项可复用 source_type + source_record_id 机制接入。

CREATE TABLE IF NOT EXISTS `qc_issue_records` (
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

CREATE TABLE IF NOT EXISTS `qc_issue_handle_logs` (
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
