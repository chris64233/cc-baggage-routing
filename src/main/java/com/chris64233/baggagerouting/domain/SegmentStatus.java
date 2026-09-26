package com.chris64233.baggagerouting.domain;

/**
 * 计划航段状态。改签只会取消 PLANNED 航段；
 * LOADED / COMPLETED 航段代表已发生的事实，任何路由重建都不得修改。
 */
public enum SegmentStatus {
    PLANNED,
    LOADED,
    COMPLETED,
    CANCELLED
}
