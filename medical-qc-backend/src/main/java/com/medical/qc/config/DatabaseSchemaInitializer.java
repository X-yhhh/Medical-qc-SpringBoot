package com.medical.qc.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 数据库增量表结构初始化器。
 *
 * <p>用于在应用启动时补齐本次改造新增的业务表，避免因历史数据库未执行增量脚本导致
 * 首页、异常汇总页访问时报“表不存在”错误。</p>
 */
@Component
public class DatabaseSchemaInitializer implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseSchemaInitializer.class);

    private final DataSource dataSource;

    public DatabaseSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Spring Boot 启动完成后执行增量建表脚本。
     *
     * <p>脚本均使用 IF NOT EXISTS，支持重复执行。</p>
     *
     * @param args 启动参数
     */
    @Override
    public void run(String... args) {
        try {
            ensureUserAuthSchema();
        } catch (Exception exception) {
            logger.error("User auth schema initialization failed.", exception);
        }

        executeIncrementalScripts();

        try {
            ensureHemorrhageRecordSchema();
        } catch (Exception exception) {
            logger.error("Hemorrhage record schema initialization failed.", exception);
        }

        try {
            ensureQualityPatientInfoSchema();
        } catch (Exception exception) {
            logger.error("Quality patient info schema initialization failed.", exception);
        }
    }

    /**
     * 执行新增业务表的建表脚本。
     */
    private void executeIncrementalScripts() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setSqlScriptEncoding("UTF-8");
        populator.addScript(new ClassPathResource("sql/create_qc_history_tables_v1.sql"));
        populator.addScript(new ClassPathResource("sql/create_qc_issue_tables_v1.sql"));
        populator.addScript(new ClassPathResource("sql/create_quality_patient_tables_v1.sql"));

        try {
            DatabasePopulatorUtils.execute(populator, dataSource);
            logger.info("QC incremental schema initialization completed.");
        } catch (Exception exception) {
            logger.error("QC incremental schema initialization failed.", exception);
        }
    }

    private void ensureUserAuthSchema() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            if (!tableExists(connection, "user_roles")) {
                statement.execute("""
                        CREATE TABLE `user_roles` (
                          `id` int NOT NULL AUTO_INCREMENT,
                          `name` varchar(20) NOT NULL,
                          `description` varchar(100) DEFAULT NULL,
                          `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
                          PRIMARY KEY (`id`),
                          UNIQUE KEY `uk_user_roles_name` (`name`)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                        """);
                logger.info("Created table user_roles.");
            }

            upsertUserRole(connection, 1, "admin", "系统管理员");
            upsertUserRole(connection, 2, "doctor", "医生");

            if (!tableExists(connection, "users")) {
                statement.execute("""
                        CREATE TABLE `users` (
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
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                        """);
                logger.info("Created table users.");
                return;
            }

            addColumnIfMissing(connection, statement, "users", "full_name",
                    "ALTER TABLE `users` ADD COLUMN `full_name` varchar(100) DEFAULT NULL AFTER `password_hash`");
            addColumnIfMissing(connection, statement, "users", "hospital",
                    "ALTER TABLE `users` ADD COLUMN `hospital` varchar(100) DEFAULT NULL AFTER `full_name`");
            addColumnIfMissing(connection, statement, "users", "department",
                    "ALTER TABLE `users` ADD COLUMN `department` varchar(50) DEFAULT NULL AFTER `hospital`");
            addColumnIfMissing(connection, statement, "users", "role_id",
                    "ALTER TABLE `users` ADD COLUMN `role_id` int NOT NULL DEFAULT 2 AFTER `department`");
            addColumnIfMissing(connection, statement, "users", "is_active",
                    "ALTER TABLE `users` ADD COLUMN `is_active` tinyint(1) NOT NULL DEFAULT 1 AFTER `role_id`");
            addColumnIfMissing(connection, statement, "users", "created_at",
                    "ALTER TABLE `users` ADD COLUMN `created_at` datetime DEFAULT CURRENT_TIMESTAMP AFTER `is_active`");
            addColumnIfMissing(connection, statement, "users", "updated_at",
                    "ALTER TABLE `users` ADD COLUMN `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER `created_at`");
            addColumnIfMissing(connection, statement, "users", "access_token",
                    "ALTER TABLE `users` ADD COLUMN `access_token` varchar(255) DEFAULT NULL AFTER `updated_at`");

            statement.executeUpdate("UPDATE `users` SET `role_id` = 2 WHERE `role_id` IS NULL");
            statement.executeUpdate("UPDATE `users` SET `is_active` = 1 WHERE `is_active` IS NULL");

            dropIndexIfExists(connection, statement, "users", "uk_users_username");
            dropIndexIfExists(connection, statement, "users", "username");
            addUniqueIndexIfMissing(connection, statement, "users", "uk_users_username_role",
                    "ALTER TABLE `users` ADD CONSTRAINT `uk_users_username_role` UNIQUE (`username`, `role_id`)");
        }
    }

    /**
     * 补齐脑出血历史表的增量字段与索引。
     *
     * <p>历史库中可能只存在旧版 hemorrhage_records 结构；若不补齐这些字段，
     * 脑出血检测在插入扩展结果时会直接报 500。</p>
     *
     * @throws SQLException 数据库访问异常
     */
    private void ensureHemorrhageRecordSchema() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            if (!tableExists(connection, "hemorrhage_records")) {
                statement.execute("""
                        CREATE TABLE `hemorrhage_records` (
                          `id` bigint NOT NULL AUTO_INCREMENT,
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
                          KEY `idx_hemorrhage_user_created_at` (`user_id`, `created_at`),
                          CONSTRAINT `hemorrhage_records_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                        """);
                logger.info("Created table hemorrhage_records.");
                return;
            }

            addColumnIfMissing(connection, statement, "hemorrhage_records", "qc_status",
                    "ALTER TABLE `hemorrhage_records` ADD COLUMN `qc_status` varchar(20) DEFAULT NULL COMMENT '质控结论：合格/不合格' AFTER `prediction`");
            addColumnIfMissing(connection, statement, "hemorrhage_records", "patient_code",
                    "ALTER TABLE `hemorrhage_records` ADD COLUMN `patient_code` varchar(100) DEFAULT NULL AFTER `patient_name`");
            addColumnIfMissing(connection, statement, "hemorrhage_records", "gender",
                    "ALTER TABLE `hemorrhage_records` ADD COLUMN `gender` varchar(20) DEFAULT NULL AFTER `exam_id`");
            addColumnIfMissing(connection, statement, "hemorrhage_records", "age",
                    "ALTER TABLE `hemorrhage_records` ADD COLUMN `age` int DEFAULT NULL AFTER `gender`");
            addColumnIfMissing(connection, statement, "hemorrhage_records", "study_date",
                    "ALTER TABLE `hemorrhage_records` ADD COLUMN `study_date` date DEFAULT NULL AFTER `age`");
            addColumnIfMissing(connection, statement, "hemorrhage_records", "midline_shift",
                    "ALTER TABLE `hemorrhage_records` ADD COLUMN `midline_shift` tinyint(1) DEFAULT NULL");
            addColumnIfMissing(connection, statement, "hemorrhage_records", "shift_score",
                    "ALTER TABLE `hemorrhage_records` ADD COLUMN `shift_score` float DEFAULT NULL");
            addColumnIfMissing(connection, statement, "hemorrhage_records", "midline_detail",
                    "ALTER TABLE `hemorrhage_records` ADD COLUMN `midline_detail` varchar(255) DEFAULT NULL");
            addColumnIfMissing(connection, statement, "hemorrhage_records", "ventricle_issue",
                    "ALTER TABLE `hemorrhage_records` ADD COLUMN `ventricle_issue` tinyint(1) DEFAULT NULL");
            addColumnIfMissing(connection, statement, "hemorrhage_records", "ventricle_detail",
                    "ALTER TABLE `hemorrhage_records` ADD COLUMN `ventricle_detail` varchar(255) DEFAULT NULL");
            addColumnIfMissing(connection, statement, "hemorrhage_records", "device",
                    "ALTER TABLE `hemorrhage_records` ADD COLUMN `device` varchar(100) DEFAULT NULL");
            addColumnIfMissing(connection, statement, "hemorrhage_records", "raw_result_json",
                    "ALTER TABLE `hemorrhage_records` ADD COLUMN `raw_result_json` longtext DEFAULT NULL");
            addColumnIfMissing(connection, statement, "hemorrhage_records", "updated_at",
                    "ALTER TABLE `hemorrhage_records` ADD COLUMN `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");

            statement.executeUpdate("""
                    UPDATE `hemorrhage_records`
                    SET `qc_status` = CASE
                      WHEN `prediction` = '出血' THEN '不合格'
                      ELSE '合格'
                    END
                    WHERE `qc_status` IS NULL OR `qc_status` = ''
                    """);

            statement.executeUpdate("""
                    UPDATE `hemorrhage_records`
                    SET `patient_code` = `exam_id`
                    WHERE (`patient_code` IS NULL OR `patient_code` = '')
                      AND `exam_id` IS NOT NULL AND `exam_id` <> ''
                    """);

            statement.executeUpdate("""
                    UPDATE `hemorrhage_records`
                    SET `study_date` = DATE(`created_at`)
                    WHERE `study_date` IS NULL AND `created_at` IS NOT NULL
                    """);

            statement.executeUpdate("""
                    UPDATE `hemorrhage_records`
                    SET `device` = 'cuda'
                    WHERE `device` IS NULL OR LOWER(`device`) <> 'cuda'
                    """);

            addIndexIfMissing(connection, statement, "hemorrhage_records", "idx_hemorrhage_user_created_at",
                    "ALTER TABLE `hemorrhage_records` ADD INDEX `idx_hemorrhage_user_created_at` (`user_id`, `created_at`)");
        }
    }

    /**
     * 为五个质控项患者信息表补齐影像图片字段。
     *
     * <p>用于兼容上一轮已创建但尚未包含 `image_path` 字段的历史数据库。</p>
     */
    private void ensureQualityPatientInfoSchema() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            String[] patientInfoTables = new String[] {
                    "head_patient_info",
                    "hemorrhage_patient_info",
                    "chest_non_contrast_patient_info",
                    "chest_contrast_patient_info",
                    "coronary_cta_patient_info"
            };

            for (String tableName : patientInfoTables) {
                if (!tableExists(connection, tableName)) {
                    continue;
                }

                addColumnIfMissing(connection, statement, tableName, "image_path",
                        "ALTER TABLE `" + tableName + "` ADD COLUMN `image_path` varchar(500) DEFAULT NULL COMMENT '患者影像图片路径' AFTER `study_date`");
            }
        }
    }

    private void upsertUserRole(Connection connection, int id, String name, String description) throws SQLException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "INSERT INTO user_roles (id, name, description) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description)")) {
            preparedStatement.setInt(1, id);
            preparedStatement.setString(2, name);
            preparedStatement.setString(3, description);
            preparedStatement.executeUpdate();
        }
    }

    /**
     * 若表不存在指定字段，则执行增量补齐。
     */
    private void addColumnIfMissing(Connection connection,
                                    Statement statement,
                                    String tableName,
                                    String columnName,
                                    String alterSql) throws SQLException {
        if (columnExists(connection, tableName, columnName)) {
            return;
        }

        statement.execute(alterSql);
        logger.info("Added column {}.{}.", tableName, columnName);
    }

    /**
     * 若表不存在指定索引，则执行增量补齐。
     */
    private void addIndexIfMissing(Connection connection,
                                   Statement statement,
                                   String tableName,
                                   String indexName,
                                   String alterSql) throws SQLException {
        if (indexExists(connection, tableName, indexName)) {
            return;
        }

        statement.execute(alterSql);
        logger.info("Added index {} on {}.", indexName, tableName);
    }

    /**
     * 若表不存在指定唯一索引，则执行增量补齐。
     */
    private void addUniqueIndexIfMissing(Connection connection,
                                         Statement statement,
                                         String tableName,
                                         String indexName,
                                         String alterSql) throws SQLException {
        if (indexExists(connection, tableName, indexName)) {
            return;
        }

        statement.execute(alterSql);
        logger.info("Added unique index {} on {}.", indexName, tableName);
    }

    /**
     * 若表存在指定索引，则执行删除。
     */
    private void dropIndexIfExists(Connection connection,
                                   Statement statement,
                                   String tableName,
                                   String indexName) throws SQLException {
        if (!indexExists(connection, tableName, indexName)) {
            return;
        }

        statement.execute("ALTER TABLE `" + tableName + "` DROP INDEX `" + indexName + "`");
        logger.info("Dropped index {} on {}.", indexName, tableName);
    }

    /**
     * 判断指定表是否存在。
     */
    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        return exists(connection,
                "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                tableName);
    }

    /**
     * 判断指定字段是否存在。
     */
    private boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?")) {
            preparedStatement.setString(1, tableName);
            preparedStatement.setString(2, columnName);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) > 0;
            }
        }
    }

    /**
     * 判断指定索引是否存在。
     */
    private boolean indexExists(Connection connection, String tableName, String indexName) throws SQLException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?")) {
            preparedStatement.setString(1, tableName);
            preparedStatement.setString(2, indexName);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) > 0;
            }
        }
    }

    /**
     * 执行存在性查询。
     */
    private boolean exists(Connection connection, String sql, String value) throws SQLException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, value);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) > 0;
            }
        }
    }
}
