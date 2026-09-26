package com.chris64233.baggagerouting.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 建行李请求：唯一标签、旅客行程与按顺序排列的航段。 */
public record CreateBaggageRequest(
        @NotBlank @Size(max = 64) String tag,
        @NotBlank @Size(max = 64) String passengerItinerary,
        @NotBlank @Size(max = 128) String passengerName,
        @NotEmpty @Valid List<SegmentPlan> segments) {
}
