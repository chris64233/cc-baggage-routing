package com.chris64233.baggagerouting.domain;

/**
 * 路线来源：旅客初始行程、改签替代路线、误装接续计划。
 */
public enum RouteSource {
    INITIAL,
    REBOOK,
    MISLOAD_RECOVERY
}
