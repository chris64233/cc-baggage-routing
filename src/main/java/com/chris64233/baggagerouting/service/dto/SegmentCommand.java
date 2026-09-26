package com.chris64233.baggagerouting.service.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 路由航段输入：航班号、起讫机场代码。
 */
public record SegmentCommand(
        @NotBlank String flightNo,
        @NotBlank String origin,
        @NotBlank String destination) {
}
