package com.medical.qc.common.exception;

/**
 * 无权限访问异常。
 * 用于统一表达“当前用户已登录但不具备访问权限”的场景。
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
