package com.chris64233.baggagerouting.service;

import static com.chris64233.baggagerouting.TestData.PEK;
import static com.chris64233.baggagerouting.TestData.PVG;
import static com.chris64233.baggagerouting.TestData.SZX;
import static com.chris64233.baggagerouting.TestData.defaultRequest;
import static com.chris64233.baggagerouting.TestData.seg;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chris64233.baggagerouting.api.ActionResultResponse;
import com.chris64233.baggagerouting.api.BaggageStatusResponse;
import com.chris64233.baggagerouting.api.EventView;
import com.chris64233.baggagerouting.api.RebookRequest;
import com.chris64233.baggagerouting.api.RoutePlanResponse;
import com.chris64233.baggagerouting.api.ScanRequest;
import com.chris64233.baggagerouting.api.SegmentPlan;
import com.chris64233.baggagerouting.api.SegmentView;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** 正常流程：建行李 → 交接 → 装载/卸载首段 → 改签尾段 → 走完新路线。 */
@SpringBootTest
class BaggageLifecycleTest {

    @Autowired
    private BaggageService baggageService;

    @Test
    void fullFlowWithRebookPreservesCompletedHistory() {
        String tag = "TAG-LIFE-1";
        baggageService.createBaggage(defaultRequest(tag));

        // 初始状态：在起点
        BaggageStatusResponse status = baggageService.currentStatus(tag);
        assertThat(status.state().status()).isEqualTo("AT_LOCATION");
        assertThat(status.state().location()).isEqualTo(PEK);
        assertThat(status.state().activeGenerationNo()).isEqualTo(1);
        assertThat(status.finalDestination()).isEqualTo(SZX);

        // 地服 -> 机坪交接（不改变物理状态）
        ActionResultResponse handover = baggageService.scan(tag, new ScanRequest(
                "EV-HO-1", "HANDOVER", null, PEK, "CHECKIN", "RAMP"));
        assertThat(handover.state().status()).isEqualTo("AT_LOCATION");
        assertThat(handover.state().location()).isEqualTo(PEK);
        assertThat(handover.eventSeq()).isEqualTo(1);

        // 装载首段
        ActionResultResponse load = baggageService.scan(tag, new ScanRequest(
                "EV-LOAD-1", "LOAD", "CA1501", PEK, null, null));
        assertThat(load.state().status()).isEqualTo("ON_SEGMENT");
        assertThat(load.state().flightNumber()).isEqualTo("CA1501");
        assertThat(load.eventSeq()).isEqualTo(2);

        // 在航段上不能重复装载
        assertThatThrownBy(() -> baggageService.scan(tag, new ScanRequest(
                "EV-LOAD-1B", "LOAD", "CA1501", PEK, null, null)))
                .isInstanceOf(BusinessRuleException.class);

        // 卸载首段到 PVG
        ActionResultResponse unload = baggageService.scan(tag, new ScanRequest(
                "EV-UNLOAD-1", "UNLOAD", "CA1501", PVG, null, null));
        assertThat(unload.state().status()).isEqualTo("AT_LOCATION");
        assertThat(unload.state().location()).isEqualTo(PVG);
        assertThat(unload.eventSeq()).isEqualTo(3);

        // 在 PVG 改签尾段：CA1893 PVG->SZX 替换为 MU5101 PVG->CAN->SZX
        List<SegmentPlan> newTail = List.of(
                seg("MU", "MU5101", PVG, "CAN"),
                seg("MU", "MU5331", "CAN", SZX));
        ActionResultResponse rebook = baggageService.rebook(tag,
                new RebookRequest("RB-1", newTail, "旅客改签到东航中转"));
        assertThat(rebook.replayed()).isFalse();
        assertThat(rebook.generationNo()).isEqualTo(2);
        assertThat(rebook.state().activeGenerationNo()).isEqualTo(2);
        assertThat(rebook.state().location()).isEqualTo(PVG);

        // 计划路线：旧世代保留（首段 COMPLETED、尾段 SUPERSEDED），新世代 ACTIVE
        RoutePlanResponse plan = baggageService.plannedRoute(tag);
        assertThat(plan.activeGenerationNo()).isEqualTo(2);
        assertThat(plan.generations()).hasSize(2);

        List<SegmentView> gen1 = plan.generations().get(0).segments();
        assertThat(gen1.get(0).status()).isEqualTo("COMPLETED");
        assertThat(gen1.get(1).status()).isEqualTo("SUPERSEDED");
        assertThat(plan.generations().get(0).status()).isEqualTo("SUPERSEDED");

        List<SegmentView> gen2 = plan.generations().get(1).segments();
        assertThat(gen2).extracting(SegmentView::flightNumber)
                .containsExactly("MU5101", "MU5331");
        assertThat(gen2).extracting(SegmentView::status)
                .containsOnly("PLANNED");

        // 旧尾段航班已不可装载（下一个可装载航段是 MU5101）
        assertThatThrownBy(() -> baggageService.scan(tag, new ScanRequest(
                "EV-LOAD-OLD", "LOAD", "CA1893", PVG, null, null)))
                .isInstanceOf(BusinessRuleException.class);

        // 走完新路线
        baggageService.scan(tag, new ScanRequest("EV-LOAD-2", "LOAD", "MU5101", PVG, null, null));
        baggageService.scan(tag, new ScanRequest("EV-UNLOAD-2", "UNLOAD", "MU5101", "CAN", null, null));
        baggageService.scan(tag, new ScanRequest("EV-LOAD-3", "LOAD", "MU5331", "CAN", null, null));
        ActionResultResponse finalUnload = baggageService.scan(tag,
                new ScanRequest("EV-UNLOAD-3", "UNLOAD", "MU5331", SZX, null, null));
        assertThat(finalUnload.state().location()).isEqualTo(SZX);

        // 实际事件流不可变：交接/装载/卸载历史完整保留，旧航段记录未被改写
        List<EventView> events = baggageService.actualEvents(tag);
        assertThat(events).extracting(EventView::type)
                .containsExactly("HANDOVER", "LOAD", "UNLOAD",
                        "LOAD", "UNLOAD", "LOAD", "UNLOAD");
        assertThat(events).extracting(EventView::eventSeq)
                .containsExactly(1, 2, 3, 4, 5, 6, 7);
        // 首段历史事件仍指向第 1 代路线
        assertThat(events.get(1).generationNo()).isEqualTo(1);
        assertThat(events.get(1).flightNumber()).isEqualTo("CA1501");
        // 改签后新事件指向第 2 代路线
        assertThat(events.get(3).generationNo()).isEqualTo(2);
    }

    @Test
    void createRejectsDisconnectedSegments() {
        assertThatThrownBy(() -> baggageService.createBaggage(
                        com.chris64233.baggagerouting.TestData.createRequest("TAG-BAD-1", List.of(
                                seg("CA1501", PEK, PVG),
                                seg("CA1893", PEK, SZX)))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("航段不衔接");
    }
}
