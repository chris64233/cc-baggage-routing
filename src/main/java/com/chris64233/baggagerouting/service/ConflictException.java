package com.chris64233.baggagerouting.service;

/** 资源冲突（如同一外部事件号内容被篡改、重复业务号内容不一致），映射为 409。 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
