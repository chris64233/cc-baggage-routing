package com.chris64233.baggagerouting.domain;

/**
 * 不可变行李事件类型。装载、卸载与交接均只能追加事件，不允许修改或删除。
 */
public enum EventType {
    /** 装载到航段载具。 */
    LOAD,
    /** 从航段载具卸载。 */
    UNLOAD,
    /** 两个责任方之间交接。 */
    HANDOVER,
    /** 误装后的异常卸载（属于 UNLOAD 类异常，独立类型便于异常链查询）。 */
    EXCEPTION_UNLOAD
}
