package com.chris64233.baggagerouting.service.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

/**
 * 行李登记：唯一标签、旅客与行程，以及按顺序排列的初始航段。
 */
public record RegisterBaggageCommand(
        @NotBlank String tag,
        @NotBlank String passengerName,
        @NotBlank String itineraryRef,
        @NotBlank String initialLocation,
        @NotEmpty @Valid List<SegmentCommand> segments) {
}
