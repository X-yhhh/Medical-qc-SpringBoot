-- 统一质控任务中心表。
-- 说明：
-- 1. 用于持久化四个异步质控模块的任务状态、结果摘要和完整结果 JSON。
-- 2. 保留 task_id 作为前端轮询主键，避免暴露数据库自增 ID。
-- 3. 后续接入真实算法时，可继续沿用该表存储任务生命周期与结果摘要。

CREATE TABLE IF NOT EXISTS `qc_task_records` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` varchar(64) NOT NULL COMMENT '前端轮询使用的任务 ID',
  `user_id` bigint NOT NULL COMMENT '提交任务的用户 ID',
  `task_type` varchar(50) NOT NULL COMMENT '质控任务类型，如 head/chest-non-contrast',
  `task_type_name` varchar(100) DEFAULT NULL COMMENT '任务类型中文名',
  `patient_name` varchar(100) DEFAULT NULL,
  `exam_id` varchar(100) DEFAULT NULL,
  `source_mode` varchar(20) NOT NULL DEFAULT 'local' COMMENT '任务来源：local/pacs',
  `original_filename` varchar(255) DEFAULT NULL,
  `stored_file_path` varchar(500) DEFAULT NULL,
  `task_status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '任务状态：PENDING/PROCESSING/SUCCESS/FAILED',
  `is_mock` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否为 mock 结果',
  `qc_status` varchar(20) DEFAULT NULL COMMENT '质控结论：合格/不合格',
  `quality_score` decimal(5,1) DEFAULT NULL COMMENT '质控评分',
  `abnormal_count` int NOT NULL DEFAULT 0 COMMENT '异常项数量',
  `primary_issue` varchar(100) DEFAULT NULL COMMENT '主异常项',
  `result_json` longtext DEFAULT NULL COMMENT '完整结果 JSON',
  `error_message` varchar(500) DEFAULT NULL COMMENT '失败原因',
  `submitted_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `started_at` datetime DEFAULT NULL,
  `completed_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_qc_task_task_id` (`task_id`),
  KEY `idx_qc_task_user_status_created` (`user_id`, `task_status`, `created_at`),
  KEY `idx_qc_task_user_type_created` (`user_id`, `task_type`, `created_at`),
  CONSTRAINT `qc_task_records_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
