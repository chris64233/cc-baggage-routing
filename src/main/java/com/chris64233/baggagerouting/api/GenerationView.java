package com.chris64233.baggagerouting.api;

import java.util.List;

/** 一个路线世代的视图（含触发来源与全部航段，含已作废/已完成的历史航段）。 */
public record GenerationView(
        int generationNo,
        String status,
        String origin,
        String destination,
        String rebookOrderNo,
        String sourceExceptionNo,
        boolean active,
        List<SegmentView> segments) {
}
