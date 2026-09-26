package com.chris64233.baggagerouting.api;

/** 行李物理状态快照：地点与在运航段二选一。 */
public record BaggageStateView(
        String status,
        String location,
        Long segmentId,
        String flightNumber,
        Integer activeGenerationNo) {
}
