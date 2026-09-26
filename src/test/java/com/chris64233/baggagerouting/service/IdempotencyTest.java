package com.chris64233.baggagerouting.service;

import static com.chris64233.baggagerouting.TestData.PEK;
import static com.chris64233.baggagerouting.TestData.PVG;
import static com.chris64233.baggagerouting.TestData.SZX;
import static com.chris64233.baggagerouting.TestData.defaultRequest;
import static com.chris64233.baggagerouting.TestData.seg;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chris64233.baggagerouting.api.ActionResultResponse;
import com.chris64233.baggagerouting.api.RebookRequest;
import com.chris64233.baggagerouting.api.ScanRequest;
import com.chris64233.baggagerouting.api.SegmentPlan;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 幂等规则：
 * 重复扫描同一外部事件号返回首次结果（不产生第二条事件）；
 * 事件内容变化返回冲突。改签业务号同理。
 */
@SpringBootTest
class IdempotencyTest {

    @Autowired
    private BaggageService baggageService;

    @Test
    void duplicateExternalScanReturnsFirstResultWithoutAppendingAnotherEvent() {
        String tag = "TAG-IDEM-1";
        baggageService.createBaggage(defaultRequest(tag));

        ScanRequest first = new ScanRequest("EV-DUP-1", "LOAD", "CA1501", PEK, null, null);
        ActionResultResponse r1 = baggageService.scan(tag, first);
        assertThat(r1.replayed()).isFalse();
        assertThat(r1.eventSeq()).isEqualTo(1);
        assertThat(r1.state().status()).isEqualTo("ON_SEGMENT");

        // 完全相同的重复扫描：返回首次结果，行李仍只在航段上，事件数不增加
        ActionResultResponse r2 = baggageService.scan(tag, first);
        assertThat(r2.replayed()).isTrue();
        assertThat(r2.action()).isEqualTo("LOAD");
        assertThat(r2.eventSeq()).isEqualTo(1);
        assertThat(r2.state().status()).isEqualTo("ON_SEGMENT");
        assertThat(baggageService.actualEvents(tag)).hasSize(1);

        // 同一外部事件号但内容变化（航班号变了）：冲突
        ScanRequest changed = new ScanRequest("EV-DUP-1", "LOAD", "CA9999", PEK, null, null);
        assertThatThrownBy(() -> baggageService.scan(tag, changed))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("内容与首次不同");
        // 冲突后状态仍是首次结果
        assertThat(baggageService.currentStatus(tag).state().flightNumber()).isEqualTo("CA1501");
        assertThat(baggageService.actualEvents(tag)).hasSize(1);
    }

    @Test
    void duplicateRebookOrderIsIdempotentButContentChangeConflicts() {
        String tag = "TAG-IDEM-2";
        baggageService.createBaggage(defaultRequest(tag));
        // 行李尚未出发、仍在 PEK，替代路线必须从 PEK 起始
        List<SegmentPlan> tail = List.of(seg("MU", "MU5101", PEK, SZX));
        RebookRequest first = new RebookRequest("RB-IDEM-1", tail, "首次改签");

        ActionResultResponse r1 = baggageService.rebook(tag, first);
        assertThat(r1.replayed()).isFalse();
        assertThat(r1.generationNo()).isEqualTo(2);

        // 同一业务号原样重放：返回首次结果，不再产生新一代
        ActionResultResponse r2 = baggageService.rebook(tag, first);
        assertThat(r2.replayed()).isTrue();
        assertThat(r2.generationNo()).isEqualTo(2);
        assertThat(baggageService.plannedRoute(tag).generations()).hasSize(2);

        // 同一业务号但路线内容变化：冲突，不产生新一代
        List<SegmentPlan> differentTail = List.of(seg("CZ", "CZ3101", PEK, SZX));
        assertThatThrownBy(() -> baggageService.rebook(tag,
                        new RebookRequest("RB-IDEM-1", differentTail, "内容变了")))
                .isInstanceOf(ConflictException.class);
        assertThat(baggageService.plannedRoute(tag).generations()).hasSize(2);
        assertThat(baggageService.currentStatus(tag).state().activeGenerationNo()).isEqualTo(2);
    }

    @Test
    void rebookRejectedOnSegmentCannotAffectFutureRoute() {
        String tag = "TAG-IDEM-3";
        baggageService.createBaggage(defaultRequest(tag));
        baggageService.scan(tag, new ScanRequest("EV-I3-L", "LOAD", "CA1501", PEK, null, null));

        // 在航段上改签：拒绝；旧路线不受影响
        assertThatThrownBy(() -> baggageService.rebook(tag,
                        new RebookRequest("RB-I3-REJECT",
                                List.of(seg("MU", "MU5101", PVG, SZX)), "在途改签")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("正在航段");

        assertThat(baggageService.plannedRoute(tag).generations()).hasSize(1);
        assertThat(baggageService.currentStatus(tag).state().status()).isEqualTo("ON_SEGMENT");
        // 该业务号没有被占用（改签未确认），卸载后可以用同一个号正常改签
        baggageService.scan(tag, new ScanRequest("EV-I3-U", "UNLOAD", "CA1501", PVG, null, null));
        ActionResultResponse rebook = baggageService.rebook(tag,
                new RebookRequest("RB-I3-REJECT",
                        List.of(seg("MU", "MU5101", PVG, SZX)), "落地后改签"));
        assertThat(rebook.generationNo()).isEqualTo(2);
    }

    @Test
    void rebookRejectsWrongStartAndWrongDestination() {
        String tag = "TAG-IDEM-4";
        baggageService.createBaggage(defaultRequest(tag));

        // 首段未执行，行李在 PEK；从错误起点 PVG 改签应拒绝
        assertThatThrownBy(() -> baggageService.rebook(tag,
                        new RebookRequest("RB-BAD-START",
                                List.of(seg("CA1", PVG, SZX)), "起点错误")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("航段不衔接");

        // 终点与行程目的地不符应拒绝
        assertThatThrownBy(() -> baggageService.rebook(tag,
                        new RebookRequest("RB-BAD-DEST",
                                List.of(seg("CA1", PEK, "CAN")), "终点错误")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("目的地");
    }
}
