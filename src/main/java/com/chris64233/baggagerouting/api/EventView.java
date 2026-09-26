package com.chris64233.baggagerouting.api;

import java.time.Instant;

/** 不可变实际事件视图。 */
public record EventView(
        int eventSeq,
        String type,
        Instant occurredAt,
        String externalEventId,
        String location,
        Long segmentId,
        Integer generationNo,
        Integer segmentNo,
        String flightNumber,
        String fromParty,
        String toParty,
        String exceptionNo,
        String note) {
}
