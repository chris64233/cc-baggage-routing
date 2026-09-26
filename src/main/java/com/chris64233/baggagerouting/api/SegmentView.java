package com.chris64233.baggagerouting.api;

/** 计划航段视图。 */
public record SegmentView(
        int segmentNo,
        String transportType,
        String carrier,
        String flightNumber,
        String origin,
        String destination,
        String status,
        String reason) {
}
