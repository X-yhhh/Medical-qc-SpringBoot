-- 异常工单 CAPA（纠正与预防措施）记录表。
-- 说明：
-- 1. 每个工单维护一条当前生效的 CAPA 记录，使用 issue_id 唯一约束。
-- 2. 根因分类、整改措施、预防措施与验证备注均放在此表，便于后续扩展独立审批流。

CREATE TABLE IF NOT EXISTS `qc_issue_capa_records` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `issue_id` bigint NOT NULL,
  `root_cause_category` varchar(50) DEFAULT NULL COMMENT '根因分类，如采集问题/设备问题/流程问题',
  `root_cause_detail` varchar(500) DEFAULT NULL COMMENT '根因说明',
  `corrective_action` text DEFAULT NULL COMMENT '纠正措施',
  `preventive_action` text DEFAULT NULL COMMENT '预防措施',
  `verification_note` varchar(500) DEFAULT NULL COMMENT '验证备注',
  `updated_by` bigint DEFAULT NULL COMMENT '最后更新人',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_qc_issue_capa_issue` (`issue_id`),
  CONSTRAINT `qc_issue_capa_records_ibfk_1` FOREIGN KEY (`issue_id`) REFERENCES `qc_issue_records` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
