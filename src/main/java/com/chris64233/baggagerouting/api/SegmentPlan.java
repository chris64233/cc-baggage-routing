package com.chris64233.baggagerouting.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 单个计划航段。相邻航段必须首尾衔接。 */
public record SegmentPlan(
        @NotBlank @Size(max = 24) String transportType,
        @NotBlank @Size(max = 64) String carrier,
        @NotBlank @Size(max = 32) String flightNumber,
        @NotBlank @Size(max = 32) String origin,
        @NotBlank @Size(max = 32) String destination) {
}
