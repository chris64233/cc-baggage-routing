package com.chris64233.baggagerouting.service;

/** 资源不存在（行李标签、业务号等）。映射 HTTP 404。 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
