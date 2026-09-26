package com.chris64233.baggagerouting.domain;

/**
 * 行李当前状态。任一时刻行李必须且只能满足以下两种情形之一：
 * 位于某个明确地点（AT_LOCATION），或正在某个运输航段上（ON_SEGMENT）。
 */
public enum BaggageStatus {
    /** 位于一个明确地点（currentLocation 有值，currentSegment 为空）。 */
    AT_LOCATION,
    /** 正在一个运输航段上（currentSegment 有值，currentLocation 为空）。 */
    ON_SEGMENT
}
