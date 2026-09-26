package com.chris64233.baggagerouting.service.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

/**
 * 改签确认：改签业务号（幂等）+ 尚未执行的后续替代航段。
 */
public record RebookCommand(
        @NotBlank String changeNo,
        @NotEmpty @Valid List<SegmentCommand> segments) {
}
