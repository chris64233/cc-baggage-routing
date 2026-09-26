package com.chris64233.baggagerouting.web.dto;

import com.chris64233.baggagerouting.domain.Baggage;

/**
 * 行李当前状态：要么 locationCode 非空（处于明确地点），要么 currentSegmentId 非空（在运输航段上）。
 */
public record BaggageStatusResponse(
        String tag,
        String passengerName,
        String itineraryRef,
        String locationCode,
        Long currentSegmentId) {

    public static BaggageStatusResponse from(Baggage baggage) {
        return new BaggageStatusResponse(
                baggage.getTag(),
                baggage.getPassengerName(),
                baggage.getItineraryRef(),
                baggage.getCurrentLocationCode(),
                baggage.getCurrentSegmentId());
    }
}
