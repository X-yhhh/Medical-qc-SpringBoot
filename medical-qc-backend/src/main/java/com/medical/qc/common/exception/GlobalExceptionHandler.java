package com.medical.qc.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.jdbc.CannotGetJdbcConnectionException;

import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CannotGetJdbcConnectionException.class)
    public ResponseEntity<Map<String, String>> handleJdbcConnectionException(CannotGetJdbcConnectionException e) {
        logger.error("Database connection failed", e);
        Map<String, String> response = new HashMap<>();
        response.put("error", "Database Connection Error");
        response.put("detail", "无法连接到数据库，请检查MySQL是否启动，以及用户名密码是否正确。");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> handleDataAccessException(DataAccessException e) {
        logger.error("Database error", e);
        Map<String, String> response = new HashMap<>();
        response.put("error", "Database Error");
        Throwable rootCause = e.getRootCause();
        String msg = rootCause != null ? rootCause.getMessage() : e.getMessage();
        response.put("detail", "数据库操作失败: " + msg);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception e) {
        logger.error("Unhandled exception", e);
        Map<String, String> response = new HashMap<>();
        response.put("error", "Internal Server Error");
        response.put("detail", "服务器内部错误: " + e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
