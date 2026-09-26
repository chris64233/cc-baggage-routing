package com.chris64233.baggagerouting.service;

import static com.chris64233.baggagerouting.TestData.CAN;
import static com.chris64233.baggagerouting.TestData.HGH;
import static com.chris64233.baggagerouting.TestData.PEK;
import static com.chris64233.baggagerouting.TestData.PVG;
import static com.chris64233.baggagerouting.TestData.SZX;
import static com.chris64233.baggagerouting.TestData.WUH;
import static com.chris64233.baggagerouting.TestData.defaultRequest;
import static com.chris64233.baggagerouting.TestData.seg;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chris64233.baggagerouting.api.ActionResultResponse;
import com.chris64233.baggagerouting.api.EventView;
import com.chris64233.baggagerouting.api.ExceptionCaseView;
import com.chris64233.baggagerouting.api.ExceptionChainResponse;
import com.chris64233.baggagerouting.api.RegisterExceptionRequest;
import com.chris64233.baggagerouting.api.RoutePlanResponse;
import com.chris64233.baggagerouting.api.ScanRequest;
import com.chris64233.baggagerouting.api.SegmentPlan;
import com.chris64233.baggagerouting.api.SegmentView;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 误装处理：必须登记异常卸载 + 接续计划，不能用普通卸载/直接改位置掩盖；
 * 多次误装通过 previousCaseNo 形成异常处理链。
 */
@SpringBootTest
class ExceptionHandlingTest {

    @Autowired
    private BaggageService baggageService;

    @Test
    void misloadIsRecordedAsExceptionUnloadWithContinuationAndChain() {
        String tag = "TAG-EXC-1";
        baggageService.createBaggage(defaultRequest(tag));
        // 系统扫描装载到计划航段 CA1501
        baggageService.scan(tag, new ScanRequest("EV-E1-LOAD", "LOAD", "CA1501", PEK, null, null));

        // 实际发现行李被错装上 CA8888，在杭州 HGH 被发现
        List<SegmentPlan> continuation = List.of(
                seg("MU", "MU5201", HGH, WUH),
                seg("MU", "MU5202", WUH, SZX));
        RegisterExceptionRequest misload = new RegisterExceptionRequest(
                null, "CA8888", HGH, "实际被装上CA8888，在杭州发现", continuation);

        ActionResultResponse result = baggageService.registerException(tag, misload);
        assertThat(result.action()).isEqualTo("EXCEPTION_UNLOAD");
        assertThat(result.referenceId()).isEqualTo(tag + "-EX-001");
        assertThat(result.generationNo()).isEqualTo(2);
        // 位置只能通过异常卸载事件迁移：现在在发现地 HGH
        assertThat(result.state().status()).isEqualTo("AT_LOCATION");
        assertThat(result.state().location()).isEqualTo(HGH);
        assertThat(result.state().activeGenerationNo()).isEqualTo(2);

        // 路线历史：第1代 ABORTED，CA1501 ABORTED，未执行的 CA1893 CANCELLED（已完成历史不变）
        RoutePlanResponse plan = baggageService.plannedRoute(tag);
        assertThat(plan.generations()).hasSize(2);
        assertThat(plan.generations().get(0).status()).isEqualTo("ABORTED");
        List<SegmentView> gen1 = plan.generations().get(0).segments();
        assertThat(gen1.get(0).status()).isEqualTo("ABORTED");
        assertThat(gen1.get(1).status()).isEqualTo("CANCELLED");
        List<SegmentView> gen2 = plan.generations().get(1).segments();
        assertThat(gen2).extracting(SegmentView::flightNumber)
                .containsExactly("MU5201", "MU5202");

        // 实际事件：EXCEPTION_UNLOAD 不可变，记录误装航班/发现地/异常号
        List<EventView> events = baggageService.actualEvents(tag);
        assertThat(events).extracting(EventView::type).containsExactly("LOAD", "EXCEPTION_UNLOAD");
        EventView exceptionEvent = events.get(1);
        assertThat(exceptionEvent.location()).isEqualTo(HGH);
        assertThat(exceptionEvent.flightNumber()).isEqualTo("CA1501");
        assertThat(exceptionEvent.exceptionNo()).isEqualTo(tag + "-EX-001");
        assertThat(exceptionEvent.note()).contains("CA8888");

        // 异常处理链
        ExceptionChainResponse chain = baggageService.exceptionChain(tag);
        assertThat(chain.caseCount()).isEqualTo(1);
        ExceptionCaseView case1 = chain.cases().get(0);
        assertThat(case1.caseNo()).isEqualTo(tag + "-EX-001");
        assertThat(case1.previousCaseNo()).isNull();
        assertThat(case1.actualFlightNumber()).isEqualTo("CA8888");
        assertThat(case1.abortedFlightNumber()).isEqualTo("CA1501");
        assertThat(case1.foundAtLocation()).isEqualTo(HGH);
        assertThat(case1.continuationGenerationNo()).isEqualTo(2);

        // 第二次误装：走接续路线首段 MU5201，又被错装，在长沙 CSX 发现
        baggageService.scan(tag, new ScanRequest("EV-E2-LOAD", "LOAD", "MU5201", HGH, null, null));
        List<SegmentPlan> continuation2 = List.of(seg("MU", "MU6001", "CSX", SZX));
        baggageService.registerException(tag, new RegisterExceptionRequest(
                null, "MU9999", "CSX", "再次错装", continuation2));

        ExceptionChainResponse chain2 = baggageService.exceptionChain(tag);
        assertThat(chain2.caseCount()).isEqualTo(2);
        assertThat(chain2.cases().get(0).caseNo()).isEqualTo(tag + "-EX-001");
        ExceptionCaseView case2 = chain2.cases().get(1);
        assertThat(case2.caseNo()).isEqualTo(tag + "-EX-002");
        assertThat(case2.previousCaseNo()).isEqualTo(tag + "-EX-001");
        assertThat(case2.continuationGenerationNo()).isEqualTo(3);
        assertThat(baggageService.currentStatus(tag).state().location()).isEqualTo("CSX");
        assertThat(baggageService.currentStatus(tag).state().activeGenerationNo()).isEqualTo(3);
    }

    @Test
    void normalUnloadAtWrongPlaceIsRejectedAndDoesNotOverwriteLocation() {
        String tag = "TAG-EXC-2";
        baggageService.createBaggage(defaultRequest(tag));
        baggageService.scan(tag, new ScanRequest("EV-X2-L", "LOAD", "CA1501", PEK, null, null));

        // 不允许把去 PVG 的航段“普通卸载”到 HGH —— 必须走异常登记，位置不得被掩盖
        assertThatThrownBy(() -> baggageService.scan(tag,
                        new ScanRequest("EV-X2-WRONG-U", "UNLOAD", "CA1501", HGH, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("登记异常");

        // 行李仍在航段 CA1501 上，位置没有被覆盖成 HGH
        assertThat(baggageService.currentStatus(tag).state().status()).isEqualTo("ON_SEGMENT");
        assertThat(baggageService.currentStatus(tag).state().flightNumber()).isEqualTo("CA1501");
        assertThat(baggageService.actualEvents(tag)).hasSize(1);
    }

    @Test
    void exceptionRejectedOnGroundOrWhenFlightMatchesPlan() {
        String tag = "TAG-EXC-3";
        baggageService.createBaggage(defaultRequest(tag));

        RegisterExceptionRequest request = new RegisterExceptionRequest(
                null, "CA8888", HGH, "地面误报",
                List.of(seg("MU", "MU1", HGH, SZX)));
        // 地面行李不能登记误装（走改签）
        assertThatThrownBy(() -> baggageService.registerException(tag, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("运输航段");

        baggageService.scan(tag, new ScanRequest("EV-X3-L", "LOAD", "CA1501", PEK, null, null));
        // 实际航班与计划一致，不构成误装
        RegisterExceptionRequest notMisload = new RegisterExceptionRequest(
                null, "CA1501", PVG, "其实没错",
                List.of(seg("MU", "MU1", PVG, SZX)));
        assertThatThrownBy(() -> baggageService.registerException(tag, notMisload))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("不构成误装");

        // 接续终点必须仍是行程目的地
        RegisterExceptionRequest wrongDest = new RegisterExceptionRequest(
                null, "CA8888", HGH, "终点错",
                List.of(seg("MU", "MU1", HGH, CAN)));
        assertThatThrownBy(() -> baggageService.registerException(tag, wrongDest))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("目的地");
    }

    @Test
    void exceptionExternalEventIsIdempotentAndContentChangeConflicts() {
        String tag = "TAG-EXC-4";
        baggageService.createBaggage(defaultRequest(tag));
        baggageService.scan(tag, new ScanRequest("EV-X4-L", "LOAD", "CA1501", PEK, null, null));

        RegisterExceptionRequest request = new RegisterExceptionRequest(
                "EV-EX-DUP", "CA8888", HGH, "外部触发误装",
                List.of(seg("MU", "MU5201", HGH, SZX)));
        ActionResultResponse r1 = baggageService.registerException(tag, request);
        assertThat(r1.replayed()).isFalse();

        ActionResultResponse r2 = baggageService.registerException(tag, request);
        assertThat(r2.replayed()).isTrue();
        assertThat(r2.referenceId()).isEqualTo(tag + "-EX-001");
        assertThat(baggageService.exceptionChain(tag).caseCount()).isEqualTo(1);
        assertThat(baggageService.actualEvents(tag)).hasSize(2);

        // 同一外部事件号内容变化 -> 冲突
        RegisterExceptionRequest changed = new RegisterExceptionRequest(
                "EV-EX-DUP", "CA7777", HGH, "内容变了",
                List.of(seg("MU", "MU5201", HGH, SZX)));
        assertThatThrownBy(() -> baggageService.registerException(tag, changed))
                .isInstanceOf(ConflictException.class);
        assertThat(baggageService.exceptionChain(tag).caseCount()).isEqualTo(1);
    }
}
