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

/**
 * 全局异常处理器。
 * 数据链路：控制器或服务抛出的异常 -> GlobalExceptionHandler -> 统一 HTTP 状态码与响应体。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 统一记录系统异常，便于排查数据库、权限和未知错误。
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理数据库连接异常。
     */
    @ExceptionHandler(CannotGetJdbcConnectionException.class)
    public ResponseEntity<Map<String, String>> handleJdbcConnectionException(CannotGetJdbcConnectionException e) {
        logger.error("Database connection failed", e);
        // 连接失败通常是数据库未启动或配置不正确，返回 503 更符合服务不可用语义。
        Map<String, String> response = new HashMap<>();
        response.put("error", "Database Connection Error");
        response.put("detail", "无法连接到数据库，请检查MySQL是否启动，以及用户名密码是否正确。");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    /**
     * 处理数据库访问异常。
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> handleDataAccessException(DataAccessException e) {
        logger.error("Database error", e);
        Map<String, String> response = new HashMap<>();
        response.put("error", "Database Error");
        // 优先返回根异常信息，便于联调时快速定位 SQL 或约束问题。
        Throwable rootCause = e.getRootCause();
        String msg = rootCause != null ? rootCause.getMessage() : e.getMessage();
        response.put("detail", "数据库操作失败: " + msg);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * 处理未认证异常。
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, String>> handleUnauthorizedException(UnauthorizedException e) {
        logger.warn("Unauthorized request: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Collections.singletonMap("detail", e.getMessage()));
    }

    /**
     * 处理无权限异常。
     */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, String>> handleForbiddenException(ForbiddenException e) {
        logger.warn("Forbidden request: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Collections.singletonMap("detail", e.getMessage()));
    }

    /**
     * 处理参数非法异常。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException e) {
        logger.warn("Bad request: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Collections.singletonMap("detail", e.getMessage()));
    }

    /**
     * 兜底处理其他未捕获异常。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception e) {
        logger.error("Unhandled exception", e);
        Map<String, String> response = new HashMap<>();
        response.put("error", "Internal Server Error");
        response.put("detail", "服务器内部错误: " + e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}

