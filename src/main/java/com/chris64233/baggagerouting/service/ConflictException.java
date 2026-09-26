package com.chris64233.baggagerouting.service;

/**
 * 幂等键冲突：同一外部事件号/改签业务号重复提交但请求内容与首次不同。
 * 映射 HTTP 409。
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
