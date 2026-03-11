-- 质控规则中心配置表。
-- 说明：
-- 1. task_type + issue_type 唯一，用于配置不同模块/异常项的优先级与 SLA。
-- 2. issue_type 支持 DEFAULT 兜底规则，未命中精确异常项时回退到 DEFAULT。
-- 3. auto_create_issue / enabled 共同决定该规则是否参与异常工单自动生成。

CREATE TABLE IF NOT EXISTS `qc_rule_configs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_type` varchar(50) NOT NULL COMMENT '模块类型，如 hemorrhage/head/chest-contrast',
  `task_type_name` varchar(100) DEFAULT NULL COMMENT '模块中文名',
  `issue_type` varchar(100) NOT NULL COMMENT '异常项名称，支持 DEFAULT 兜底规则',
  `priority` varchar(20) NOT NULL DEFAULT '中' COMMENT '优先级：高/中/低',
  `responsible_role` varchar(20) NOT NULL DEFAULT 'doctor' COMMENT '责任角色：admin/doctor',
  `sla_hours` int NOT NULL DEFAULT 24 COMMENT 'SLA 时限（小时）',
  `auto_create_issue` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否自动生成工单',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用规则',
  `description` varchar(255) DEFAULT NULL COMMENT '规则说明',
  `updated_by` bigint DEFAULT NULL COMMENT '最后更新管理员 ID',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_qc_rule_task_issue` (`task_type`, `issue_type`),
  KEY `idx_qc_rule_task_enabled` (`task_type`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `qc_rule_configs`
(`task_type`, `task_type_name`, `issue_type`, `priority`, `responsible_role`, `sla_hours`, `auto_create_issue`, `enabled`, `description`)
VALUES
('hemorrhage', '头部出血检测', 'DEFAULT', '中', 'doctor', 8, 1, 1, '头部出血检测默认规则'),
('hemorrhage', '头部出血检测', '脑出血', '高', 'doctor', 2, 1, 1, '脑出血属于急危异常，需优先处理'),
('hemorrhage', '头部出血检测', '中线偏移', '高', 'doctor', 4, 1, 1, '中线偏移提示高风险占位效应'),
('hemorrhage', '头部出血检测', '脑室结构异常', '中', 'doctor', 8, 1, 1, '脑室结构异常需尽快复核'),
('head', 'CT头部平扫质控', 'DEFAULT', '低', 'doctor', 24, 1, 1, 'CT头部平扫默认规则'),
('head', 'CT头部平扫质控', '运动伪影', '中', 'doctor', 8, 1, 1, '运动伪影明显影响诊断质量'),
('head', 'CT头部平扫质控', '金属伪影', '低', 'doctor', 24, 1, 1, '金属伪影可结合临床情况处理'),
('chest-non-contrast', 'CT胸部平扫质控', 'DEFAULT', '低', 'doctor', 24, 1, 1, 'CT胸部平扫默认规则'),
('chest-non-contrast', 'CT胸部平扫质控', '呼吸伪影', '中', 'doctor', 12, 1, 1, '呼吸伪影影响肺野观察'),
('chest-contrast', 'CT胸部增强质控', 'DEFAULT', '中', 'doctor', 12, 1, 1, 'CT胸部增强默认规则'),
('chest-contrast', 'CT胸部增强质控', '静脉污染', '高', 'doctor', 4, 1, 1, '静脉污染可能显著影响增强期判读'),
('coronary-cta', '冠脉CTA质控', 'DEFAULT', '中', 'doctor', 8, 1, 1, '冠脉CTA默认规则'),
('coronary-cta', '冠脉CTA质控', '血管强化 (RCA)', '高', 'doctor', 4, 1, 1, '冠脉强化不足需优先处理'),
('coronary-cta', '冠脉CTA质控', '呼吸配合', '中', 'doctor', 8, 1, 1, '屏气不佳影响冠脉连续性评估')
ON DUPLICATE KEY UPDATE
`task_type_name` = VALUES(`task_type_name`),
`priority` = VALUES(`priority`),
`responsible_role` = VALUES(`responsible_role`),
`sla_hours` = VALUES(`sla_hours`),
`auto_create_issue` = VALUES(`auto_create_issue`),
`enabled` = VALUES(`enabled`),
`description` = VALUES(`description`);
