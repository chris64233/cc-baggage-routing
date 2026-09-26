package com.chris64233.baggagerouting.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 误装异常登记请求：为正在误装航段上的行李登记异常卸载与新接续计划。
 * 系统不提供“直接改位置”的入口，误装必须走该异常处理链。
 */
public record RegisterExceptionRequest(
        /** 可选：由外部扫描触发，提供则按外部事件幂等处理。 */
        @Size(max = 64) String externalEventId,
        @NotBlank @Size(max = 32) String actualFlightNumber,
        @NotBlank @Size(max = 32) String foundAtLocation,
        @NotBlank @Size(max = 1000) String reason,
        @NotEmpty @Valid List<SegmentPlan> continuationSegments) {
}
