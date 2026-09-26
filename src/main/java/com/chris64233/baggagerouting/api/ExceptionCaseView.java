package com.chris64233.baggagerouting.api;

import java.time.Instant;
import java.util.List;

/** 单次误装异常处理的视图，previousCaseNo 将多次误装串成处理链。 */
public record ExceptionCaseView(
        String caseNo,
        String previousCaseNo,
        String externalEventId,
        Instant registeredAt,
        String abortedFlightNumber,
        Integer abortedGenerationNo,
        Integer abortedSegmentNo,
        String actualFlightNumber,
        String foundAtLocation,
        String reason,
        Integer continuationGenerationNo,
        List<SegmentView> continuationSegments) {
}
