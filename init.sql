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
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Records of user_roles
-- ----------------------------
INSERT INTO `user_roles` (`id`, `name`, `description`) VALUES (1, 'admin', 'System Administrator');
INSERT INTO `user_roles` (`id`, `name`, `description`) VALUES (2, 'doctor', 'Medical Doctor');

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
  UNIQUE KEY `username` (`username`),
  UNIQUE KEY `email` (`email`),
  UNIQUE KEY `access_token` (`access_token`),
  KEY `role_id` (`role_id`),
  CONSTRAINT `users_ibfk_1` FOREIGN KEY (`role_id`) REFERENCES `user_roles` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- Table structure for hemorrhage_records
-- ----------------------------
DROP TABLE IF EXISTS `hemorrhage_records`;
CREATE TABLE `hemorrhage_records` (
  `id` int NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `patient_name` varchar(100) DEFAULT NULL,
  `exam_id` varchar(100) DEFAULT NULL,
  `image_path` varchar(500) NOT NULL,
  `prediction` varchar(50) NOT NULL,
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
  CONSTRAINT `hemorrhage_records_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET FOREIGN_KEY_CHECKS = 1;
