package com.chris64233.baggagerouting.service;

/**
 * 业务规则冲突：当前状态不允许该操作（在航段上改签、航段不衔接、
 * 扫描航班与计划不符等）。映射 HTTP 422。
 */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
