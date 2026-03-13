package com.medical.qc.common.exception;

/**
 * 未认证异常。
 * 用于统一表达“当前请求缺少有效登录会话”的场景。
 */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}

