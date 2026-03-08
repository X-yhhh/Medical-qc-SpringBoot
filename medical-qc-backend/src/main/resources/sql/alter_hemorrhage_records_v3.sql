-- 为 hemorrhage_records 补充首页实时展示所需字段。
-- 本次改造目标：
-- 1. 将“合格 / 不合格”作为历史检测记录的持久化字段保存。
-- 2. 为首页“最近访问”提供按用户和时间排序的查询索引。

ALTER TABLE `hemorrhage_records`
  ADD COLUMN `qc_status` varchar(20) DEFAULT NULL COMMENT '质控结论：合格/不合格' AFTER `prediction`;

UPDATE `hemorrhage_records`
SET `qc_status` = CASE
  WHEN `prediction` = '出血' THEN '不合格'
  ELSE '合格'
END
WHERE `qc_status` IS NULL;

ALTER TABLE `hemorrhage_records`
  MODIFY COLUMN `qc_status` varchar(20) NOT NULL COMMENT '质控结论：合格/不合格';

ALTER TABLE `hemorrhage_records`
  ADD INDEX `idx_hemorrhage_user_created_at` (`user_id`, `created_at`);
