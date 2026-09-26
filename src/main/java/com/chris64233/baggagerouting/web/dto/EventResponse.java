package com.chris64233.baggagerouting.web.dto;

import java.time.Instant;

import com.chris64233.baggagerouting.domain.BaggageEvent;

public record EventResponse(
        Long id,
        String type,
        String flightNo,
        String locationCode,
        String externalEventRef,
        Instant occurredAt,
        Instant recordedAt) {

    public static EventResponse from(BaggageEvent event) {
        return new EventResponse(
                event.getId(),
                event.getType().name(),
                event.getFlightNo(),
                event.getLocationCode(),
                event.getExternalEventRef(),
                event.getOccurredAt(),
                event.getRecordedAt());
    }
}
