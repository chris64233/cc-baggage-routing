package com.chris64233.baggagerouting.api;

/**
 * 写操作统一结果。外部事件重放时会原样返回首次结果（replayed=true），
 * 因此该记录会被序列化为 JSON 持久化，只包含可安全往返的纯数据。
 */
public record ActionResultResponse(
        boolean replayed,
        String action,
        String referenceId,
        Integer eventSeq,
        Integer generationNo,
        String message,
        BaggageStateView state) {
}
