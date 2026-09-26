package com.chris64233.baggagerouting.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 外部扫描事件。type 取值：LOAD / UNLOAD / HANDOVER。
 * externalEventId 全局唯一，重复扫描返回首次结果，内容变化返回冲突。
 */
public record ScanRequest(
        @NotBlank @Size(max = 64) String externalEventId,
        @NotBlank @Size(max = 16) String type,
        /** 实际扫描到的航班/车次号；LOAD 时必须与计划航段一致，否则判为误装。 */
        @Size(max = 32) String flightNumber,
        /** 扫描地点：UNLOAD/HANDOVER 必填，LOAD 时可选校验。 */
        @Size(max = 32) String location,
        @Size(max = 64) String fromParty,
        @Size(max = 64) String toParty) {
}
