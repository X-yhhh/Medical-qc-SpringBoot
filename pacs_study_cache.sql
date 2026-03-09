-- PACS检查记录缓存表
USE medical_qc_sys;

DROP TABLE IF EXISTS `pacs_study_cache`;
CREATE TABLE `pacs_study_cache` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `study_instance_uid` varchar(255) NOT NULL COMMENT '检查实例UID（DICOM标准）',
  `patient_id` varchar(100) DEFAULT NULL COMMENT '患者ID',
  `patient_name` varchar(100) DEFAULT NULL COMMENT '患者姓名',
  `accession_number` varchar(100) DEFAULT NULL COMMENT '检查号（Accession Number）',
  `study_date` date DEFAULT NULL COMMENT '检查日期',
  `study_time` time DEFAULT NULL COMMENT '检查时间',
  `study_description` varchar(255) DEFAULT NULL COMMENT '检查描述',
  `modality` varchar(50) DEFAULT NULL COMMENT '影像模态（CT/MR/CR等）',
  `series_count` int DEFAULT 0 COMMENT '序列数量',
  `image_count` int DEFAULT 0 COMMENT '图像数量',
  `body_part` varchar(100) DEFAULT NULL COMMENT '检查部位',
  `manufacturer` varchar(100) DEFAULT NULL COMMENT '设备厂商',
  `model_name` varchar(100) DEFAULT NULL COMMENT '设备型号',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '缓存创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '缓存更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_study_instance_uid` (`study_instance_uid`),
  KEY `idx_patient_id` (`patient_id`),
  KEY `idx_patient_name` (`patient_name`),
  KEY `idx_accession_number` (`accession_number`),
  KEY `idx_study_date` (`study_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='PACS检查记录缓存表';

-- 插入模拟数据
INSERT INTO `pacs_study_cache` VALUES
(1, '1.2.840.113619.2.55.3.1234567890.123', 'P001', '张三', 'ACC001', '2026-03-08', '09:30:00', '头部CT平扫', 'CT', 5, 150, '头部', 'GE Healthcare', 'Revolution CT', NOW(), NOW()),
(2, '1.2.840.113619.2.55.3.1234567890.124', 'P002', '李四', 'ACC002', '2026-03-07', '14:20:00', '头部CT增强', 'CT', 6, 180, '头部', 'Siemens', 'SOMATOM Force', NOW(), NOW()),
(3, '1.2.840.113619.2.55.3.1234567890.125', 'P003', '王五', 'ACC003', '2026-03-06', '10:15:00', '头部CT平扫', 'CT', 4, 120, '头部', 'Philips', 'Brilliance CT', NOW(), NOW()),
(4, '1.2.840.113619.2.55.3.1234567890.126', 'P004', '赵六', 'ACC004', '2026-03-05', '15:45:00', '头部CT平扫', 'CT', 5, 145, '头部', 'GE Healthcare', 'Optima CT660', NOW(), NOW()),
(5, '1.2.840.113619.2.55.3.1234567890.127', 'P005', '孙七', 'ACC005', '2026-03-04', '11:20:00', '头部CT增强', 'CT', 7, 200, '头部', 'Siemens', 'SOMATOM Definition', NOW(), NOW());
