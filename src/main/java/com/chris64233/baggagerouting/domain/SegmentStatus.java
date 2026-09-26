package com.chris64233.baggagerouting.domain;

/** 计划航段生命周期状态。 */
public enum SegmentStatus {
    /** 计划中，尚未执行（改签/异常处理时仅这种状态可被取消）。 */
    PLANNED,
    /** 已装载，正在执行。 */
    IN_PROGRESS,
    /** 已正常卸载完成。 */
    COMPLETED,
    /** 改签确认后被原子取消的旧未执行航段。 */
    SUPERSEDED,
    /** 误装发现时，行李实际停留的航段被提前终止。 */
    ABORTED,
    /** 因前序误装而不再可达的未执行航段。 */
    CANCELLED
}
