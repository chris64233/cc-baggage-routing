package com.chris64233.baggagerouting;

import com.chris64233.baggagerouting.api.CreateBaggageRequest;
import com.chris64233.baggagerouting.api.SegmentPlan;
import java.util.List;

/** 测试数据构造辅助。 */
public final class TestData {

    public static final String PEK = "PEK";
    public static final String PVG = "PVG";
    public static final String SZX = "SZX";
    public static final String CAN = "CAN";
    public static final String HGH = "HGH";
    public static final String WUH = "WUH";

    private TestData() {
    }

    public static SegmentPlan seg(String flight, String origin, String destination) {
        return new SegmentPlan("FLIGHT", "CA", flight, origin, destination);
    }

    public static SegmentPlan seg(String carrier, String flight, String origin, String destination) {
        return new SegmentPlan("FLIGHT", carrier, flight, origin, destination);
    }

    public static CreateBaggageRequest createRequest(String tag, List<SegmentPlan> segments) {
        return new CreateBaggageRequest(tag, "PNR-" + tag, "张三", segments);
    }

    /** 默认 PEK -> PVG(CA1501) -> SZX(CA1893)。 */
    public static CreateBaggageRequest defaultRequest(String tag) {
        return createRequest(tag, List.of(
                seg("CA1501", PEK, PVG),
                seg("CA1893", PVG, SZX)));
    }
}
