package com.chris64233.baggagerouting.service.dto;

import java.time.Instant;

import com.chris64233.baggagerouting.domain.EventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 外部扫描事件：装载 / 卸载 / 交接。
 * EXCEPTION_UNLOAD 不接受直接上报，必须走误装处理接口。
 */
public record RecordEventCommand(
        @NotBlank String externalEventRef,
        @NotNull EventType type,
        String flightNo,
        @NotBlank String locationCode,
        @NotNull Instant occurredAt) {
}
