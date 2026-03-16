-- Flyway V15
-- 目标：
-- 1. 为统一质控结果补充人工复核、锁定和外部引用字段
-- 2. 新增质控结果审计日志表，记录复核、批量重跑、导出等动作

ALTER TABLE `qc_results`
  ADD COLUMN `review_status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '人工复核状态：PENDING/CONFIRMED/REJECTED' AFTER `raw_result_json`,
  ADD COLUMN `review_comment` varchar(500) DEFAULT NULL COMMENT '人工复核意见' AFTER `review_status`,
  ADD COLUMN `reviewed_by` bigint DEFAULT NULL COMMENT '复核人用户 ID' AFTER `review_comment`,
  ADD COLUMN `reviewed_at` datetime DEFAULT NULL COMMENT '复核时间' AFTER `reviewed_by`,
  ADD COLUMN `locked_at` datetime DEFAULT NULL COMMENT '结果锁定时间' AFTER `reviewed_at`,
  ADD COLUMN `external_ref` varchar(100) DEFAULT NULL COMMENT '外部系统引用号' AFTER `locked_at`;

ALTER TABLE `qc_results`
  ADD KEY `idx_qc_results_review_status` (`review_status`),
  ADD KEY `idx_qc_results_reviewed_by` (`reviewed_by`),
  ADD CONSTRAINT `qc_results_ibfk_2` FOREIGN KEY (`reviewed_by`) REFERENCES `users` (`id`) ON DELETE SET NULL;

CREATE TABLE IF NOT EXISTS `qc_result_audit_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` bigint NOT NULL,
  `result_id` bigint DEFAULT NULL,
  `operator_id` bigint DEFAULT NULL,
  `action_type` varchar(50) NOT NULL,
  `action_comment` varchar(500) DEFAULT NULL,
  `payload_json` json DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_qc_result_audit_logs_task_created` (`task_id`, `created_at`),
  KEY `idx_qc_result_audit_logs_result_created` (`result_id`, `created_at`),
  KEY `idx_qc_result_audit_logs_operator` (`operator_id`),
  CONSTRAINT `qc_result_audit_logs_ibfk_1` FOREIGN KEY (`task_id`) REFERENCES `qc_tasks` (`id`) ON DELETE CASCADE,
  CONSTRAINT `qc_result_audit_logs_ibfk_2` FOREIGN KEY (`result_id`) REFERENCES `qc_results` (`id`) ON DELETE SET NULL,
  CONSTRAINT `qc_result_audit_logs_ibfk_3` FOREIGN KEY (`operator_id`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一质控结果审计日志表';
