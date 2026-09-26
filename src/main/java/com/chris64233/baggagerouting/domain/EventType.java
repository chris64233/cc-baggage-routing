package com.chris64233.baggagerouting.domain;

/**
 * 不可变行李事件类型。
 * EXCEPTION_UNLOAD 只能由误装处理流程登记，不允许直接作为普通事件上报。
 */
public enum EventType {
    LOAD,
    UNLOAD,
    HANDOVER,
    EXCEPTION_UNLOAD
}
