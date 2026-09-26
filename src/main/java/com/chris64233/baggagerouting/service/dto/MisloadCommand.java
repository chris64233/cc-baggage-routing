package com.chris64233.baggagerouting.service.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

/**
 * 误装上报：外部事件号（幂等）+ 行李实际被发现/卸下的地点 + 新的接续航段。
 */
public record MisloadCommand(
        @NotBlank String externalEventRef,
        @NotBlank String foundLocationCode,
        @NotEmpty @Valid List<SegmentCommand> segments) {
}
