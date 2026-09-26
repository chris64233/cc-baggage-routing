package com.chris64233.baggagerouting.web.dto;

import java.time.Instant;

import com.chris64233.baggagerouting.domain.BaggageException;

public record ExceptionResponse(
        Long id,
        String type,
        Long wrongSegmentId,
        String foundLocationCode,
        Long exceptionEventId,
        Long recoveryRouteId,
        Long previousExceptionId,
        String status,
        Instant createdAt) {

    public static ExceptionResponse from(BaggageException exception) {
        return new ExceptionResponse(
                exception.getId(),
                exception.getType().name(),
                exception.getWrongSegmentId(),
                exception.getFoundLocationCode(),
                exception.getExceptionEvent().getId(),
                exception.getRecoveryRoute().getId(),
                exception.getPreviousExceptionId(),
                exception.getStatus().name(),
                exception.getCreatedAt());
    }
}
