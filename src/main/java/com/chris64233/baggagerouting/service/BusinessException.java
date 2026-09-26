package com.chris64233.baggagerouting.service;

/** 业务规则校验失败（如状态不允许的操作），映射为 422。 */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
