package com.chris64233.baggagerouting.api;

/** 行李当前状态查询结果。 */
public record BaggageStatusResponse(
        String tag,
        String passengerItinerary,
        String passengerName,
        String finalDestination,
        BaggageStateView state,
        int eventCount) {
}
