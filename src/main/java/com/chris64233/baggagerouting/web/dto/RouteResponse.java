package com.chris64233.baggagerouting.web.dto;

import java.time.Instant;
import java.util.List;

import com.chris64233.baggagerouting.domain.Route;
import com.chris64233.baggagerouting.domain.RouteSegment;

public record RouteResponse(
        Long id,
        String source,
        String status,
        String changeNo,
        Instant createdAt,
        List<SegmentResponse> segments) {

    public static RouteResponse from(Route route) {
        return new RouteResponse(
                route.getId(),
                route.getSource().name(),
                route.getStatus().name(),
                route.getChangeNo(),
                route.getCreatedAt(),
                route.getSegments().stream().map(SegmentResponse::from).toList());
    }

    public record SegmentResponse(
            Long id,
            int seq,
            String flightNo,
            String origin,
            String destination,
            String status) {

        public static SegmentResponse from(RouteSegment segment) {
            return new SegmentResponse(
                    segment.getId(),
                    segment.getSeq(),
                    segment.getFlightNo(),
                    segment.getOrigin(),
                    segment.getDestination(),
                    segment.getStatus().name());
        }
    }
}
