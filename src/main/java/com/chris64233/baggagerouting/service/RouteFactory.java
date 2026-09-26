package com.chris64233.baggagerouting.service;

import com.chris64233.baggagerouting.api.SegmentPlan;
import com.chris64233.baggagerouting.domain.RouteGeneration;
import com.chris64233.baggagerouting.domain.RouteSegment;
import java.util.List;

/** 由请求中的航段计划构造世代航段，并校验首尾衔接。 */
final class RouteFactory {

    private RouteFactory() {
    }

    /**
     * 向世代追加按顺序排列的航段。
     *
     * @param startAt 路线必须起始的地点（改签/接续时为行李当前位置；初始路线为首段起点本身）
     */
    static void appendSegments(RouteGeneration generation, List<SegmentPlan> plans, String startAt) {
        if (plans == null || plans.isEmpty()) {
            throw new BusinessRuleException("路线至少需要一个航段");
        }
        String expectedFrom = startAt;
        int no = 1;
        for (SegmentPlan plan : plans) {
            if (!expectedFrom.equals(plan.origin())) {
                throw new BusinessRuleException(
                        "航段不衔接：第 " + no + " 段应从 " + expectedFrom + " 出发，实际为 " + plan.origin());
            }
            new RouteSegment(generation, no, plan.transportType(), plan.carrier(),
                    plan.flightNumber(), plan.origin(), plan.destination());
            expectedFrom = plan.destination();
            no++;
        }
    }

    static String lastDestination(List<SegmentPlan> plans) {
        return plans.get(plans.size() - 1).destination();
    }
}
