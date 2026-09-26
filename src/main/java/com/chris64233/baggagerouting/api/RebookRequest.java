package com.chris64233.baggagerouting.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 确认改签请求。orderNo（改签业务号）是幂等键：
 * 重复提交返回首次结果，内容变化返回冲突。
 */
public record RebookRequest(
        @NotBlank @Size(max = 64) String orderNo,
        @NotEmpty @Valid List<SegmentPlan> segments,
        @Size(max = 1000) String reason) {
}
