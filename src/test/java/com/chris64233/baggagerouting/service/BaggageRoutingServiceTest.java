package com.chris64233.baggagerouting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.chris64233.baggagerouting.domain.Baggage;
import com.chris64233.baggagerouting.domain.BaggageEvent;
import com.chris64233.baggagerouting.domain.BaggageException;
import com.chris64233.baggagerouting.domain.EventType;
import com.chris64233.baggagerouting.domain.Route;
import com.chris64233.baggagerouting.domain.RouteSegment;
import com.chris64233.baggagerouting.domain.RouteSource;
import com.chris64233.baggagerouting.domain.RouteStatus;
import com.chris64233.baggagerouting.domain.SegmentStatus;
import com.chris64233.baggagerouting.repository.BaggageEventRepository;
import com.chris64233.baggagerouting.repository.BaggageExceptionRepository;
import com.chris64233.baggagerouting.repository.BaggageRepository;
import com.chris64233.baggagerouting.repository.RouteRepository;
import com.chris64233.baggagerouting.repository.RouteSegmentRepository;
import com.chris64233.baggagerouting.service.dto.MisloadCommand;
import com.chris64233.baggagerouting.service.dto.RecordEventCommand;
import com.chris64233.baggagerouting.service.dto.RebookCommand;
import com.chris64233.baggagerouting.service.dto.RegisterBaggageCommand;
import com.chris64233.baggagerouting.service.dto.SegmentCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class BaggageRoutingServiceTest {

    private static final Instant T0 = Instant.parse("2026-09-27T08:00:00Z");

    @Autowired
    private BaggageRoutingService service;
    @Autowired
    private BaggageRepository baggageRepository;
    @Autowired
    private RouteRepository routeRepository;
    @Autowired
    private RouteSegmentRepository segmentRepository;
    @Autowired
    private BaggageEventRepository eventRepository;
    @Autowired
    private BaggageExceptionRepository exceptionRepository;

    @BeforeEach
    void cleanDatabase() {
        exceptionRepository.deleteAll();
        eventRepository.deleteAll();
        routeRepository.deleteAll();
        baggageRepository.deleteAll();
    }

    // ------------------------------------------------------------ 登记

    @Test
    @DisplayName("登记行李：唯一标签、初始路线与按顺序航段，初始处于明确地点")
    void registerCreatesInitialRoute() {
        Baggage baggage = register("BAG001");

        assertThat(baggage.getCurrentLocationCode()).isEqualTo("A");
        assertThat(baggage.getCurrentSegmentId()).isNull();

        Route route = service.plannedRoute("BAG001");
        assertThat(route.getSource()).isEqualTo(RouteSource.INITIAL);
        assertThat(route.getStatus()).isEqualTo(RouteStatus.ACTIVE);
        assertThat(route.getSegments()).extracting(RouteSegment::getSeq)
                .containsExactly(1, 2);
        assertThat(route.getSegments()).extracting(RouteSegment::getStatus)
                .containsExactly(SegmentStatus.PLANNED, SegmentStatus.PLANNED);
    }

    @Test
    @DisplayName("重复行李标签登记返回冲突")
    void duplicateTagConflicts() {
        register("BAG002");
        assertThatThrownBy(() -> register("BAG002"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("航段链不连续（起点与初始地点不符）拒绝登记")
    void brokenChainRejected() {
        assertThatThrownBy(() -> service.registerBaggage(new RegisterBaggageCommand(
                "BAG003", "李四", "ITIN3", "A",
                List.of(new SegmentCommand("F1", "X", "B")))))
                .isInstanceOf(BusinessException.class);
    }

    // ------------------------------------------------------------ 装载/卸载/交接

    @Test
    @DisplayName("装载→卸载→交接全过程满足单一位置不变式")
    void loadUnloadHandoverFlow() {
        register("BAG010");

        BaggageEvent loaded = service.recordEvent("BAG010",
                event("EVT-LOAD1", EventType.LOAD, "F1", "A", T0));
        assertThat(loaded.getType()).isEqualTo(EventType.LOAD);

        Baggage onSegment = service.currentStatus("BAG010");
        assertThat(onSegment.getCurrentLocationCode()).isNull();
        assertThat(onSegment.getCurrentSegmentId()).isNotNull();

        assertThat(segmentByFlight("BAG010", "F1").getStatus())
                .isEqualTo(SegmentStatus.LOADED);

        // 运输途中不能交接。
        assertThatThrownBy(() -> service.recordEvent("BAG010",
                event("EVT-HO-X", EventType.HANDOVER, null, "B", T0.plusSeconds(60))))
                .isInstanceOf(BusinessException.class);

        // 同一时刻不能重复装载。
        assertThatThrownBy(() -> service.recordEvent("BAG010",
                event("EVT-LOAD-DUP", EventType.LOAD, "F2", "B", T0.plusSeconds(60))))
                .isInstanceOf(BusinessException.class);

        service.recordEvent("BAG010",
                event("EVT-UNLOAD1", EventType.UNLOAD, "F1", "B", T0.plusSeconds(120)));
        Baggage atB = service.currentStatus("BAG010");
        assertThat(atB.getCurrentLocationCode()).isEqualTo("B");
        assertThat(atB.getCurrentSegmentId()).isNull();
        assertThat(segmentByFlight("BAG010", "F1").getStatus())
                .isEqualTo(SegmentStatus.COMPLETED);

        service.recordEvent("BAG010",
                event("EVT-HO1", EventType.HANDOVER, null, "B", T0.plusSeconds(180)));
        assertThat(service.eventHistory("BAG010")).hasSize(3);
    }

    @Test
    @DisplayName("装载航班或地点与计划航段不符时拒绝")
    void loadWithWrongFlightOrLocationRejected() {
        register("BAG011");

        assertThatThrownBy(() -> service.recordEvent("BAG011",
                event("EVT-WF", EventType.LOAD, "F9", "A", T0)))
                .isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> service.recordEvent("BAG011",
                event("EVT-WL", EventType.LOAD, "F1", "X", T0)))
                .isInstanceOf(BusinessException.class);

        // 被拒后行李仍在原地点。
        Baggage unchanged = service.currentStatus("BAG011");
        assertThat(unchanged.getCurrentLocationCode()).isEqualTo("A");
        assertThat(unchanged.getCurrentSegmentId()).isNull();
    }

    @Test
    @DisplayName("在计划外地点卸载被拒绝，必须走误装流程，不允许直接覆盖位置")
    void unloadAtUnexpectedLocationRejected() {
        register("BAG012");
        service.recordEvent("BAG012", event("EVT-L", EventType.LOAD, "F1", "A", T0));

        assertThatThrownBy(() -> service.recordEvent("BAG012",
                event("EVT-UX", EventType.UNLOAD, "F1", "X", T0.plusSeconds(60))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("误装");

        Baggage stillOnSegment = service.currentStatus("BAG012");
        assertThat(stillOnSegment.getCurrentSegmentId()).isNotNull();
        assertThat(stillOnSegment.getCurrentLocationCode()).isNull();
    }

    @Test
    @DisplayName("EXCEPTION_UNLOAD 不允许作为普通事件直接上报")
    void exceptionUnloadNotAcceptedAsNormalEvent() {
        register("BAG013");
        assertThatThrownBy(() -> service.recordEvent("BAG013",
                event("EVT-EU", EventType.EXCEPTION_UNLOAD, null, "A", T0)))
                .isInstanceOf(BusinessException.class);
    }

    // ------------------------------------------------------------ 事件幂等

    @Test
    @DisplayName("重复扫描同一外部事件返回首次结果；内容变化返回冲突，且不改变位置")
    void externalEventIdempotencyAndConflict() {
        register("BAG020");

        BaggageEvent first = service.recordEvent("BAG020",
                event("EVT-SAME", EventType.LOAD, "F1", "A", T0));
        BaggageEvent replay = service.recordEvent("BAG020",
                event("EVT-SAME", EventType.LOAD, "F1", "A", T0));

        assertThat(replay.getId()).isEqualTo(first.getId());
        assertThat(service.eventHistory("BAG020")).hasSize(1);

        assertThatThrownBy(() -> service.recordEvent("BAG020",
                event("EVT-SAME", EventType.LOAD, "F1", "B", T0)))
                .isInstanceOf(ConflictException.class);

        // 冲突上报不改变行李位置：仍处于 F1 航段上。
        Baggage statusAfterConflict = service.currentStatus("BAG020");
        assertThat(statusAfterConflict.getCurrentSegmentId()).isNotNull();
        assertThat(statusAfterConflict.getCurrentLocationCode()).isNull();
        assertThat(service.eventHistory("BAG020")).hasSize(1);
    }

    // ------------------------------------------------------------ 改签

    @Test
    @DisplayName("改签一次性取消旧的未执行航段并创建新路线；已完成航段和既有交接记录不得改变")
    void rebookAtomicallyReplacesUnexecutedRoute() {
        register("BAG030");
        service.recordEvent("BAG030", event("EVT-L1", EventType.LOAD, "F1", "A", T0));
        service.recordEvent("BAG030", event("EVT-U1", EventType.UNLOAD, "F1", "B", T0.plusSeconds(60)));
        service.recordEvent("BAG030", event("EVT-H1", EventType.HANDOVER, null, "B", T0.plusSeconds(120)));
        RouteSegment completedF1 = segmentByFlight("BAG030", "F1");
        List<BaggageEvent> eventsBeforeRebook = service.eventHistory("BAG030");

        Route newRoute = service.confirmRebooking("BAG030", new RebookCommand(
                "CHANGE-1", List.of(new SegmentCommand("F5", "B", "D"))));

        assertThat(newRoute.getSource()).isEqualTo(RouteSource.REBOOK);
        assertThat(newRoute.getStatus()).isEqualTo(RouteStatus.ACTIVE);
        assertThat(newRoute.getChangeNo()).isEqualTo("CHANGE-1");
        assertThat(service.plannedRoute("BAG030").getId()).isEqualTo(newRoute.getId());

        List<Route> routes = service.routeHistory("BAG030");
        assertThat(routes).hasSize(2);
        Route oldRoute = routes.get(0);
        assertThat(oldRoute.getStatus()).isEqualTo(RouteStatus.CANCELLED);

        // 已完成航段保持 COMPLETED，未执行航段被取消。
        RouteSegment f1After = segmentById(completedF1.getId());
        assertThat(f1After.getStatus()).isEqualTo(SegmentStatus.COMPLETED);
        assertThat(segmentByFlight("BAG030", "F2").getStatus())
                .isEqualTo(SegmentStatus.CANCELLED);

        // 既有事件（含交接）原样保留，改签不产生新事件。
        assertThat(service.eventHistory("BAG030"))
                .extracting(BaggageEvent::getId)
                .isEqualTo(eventsBeforeRebook.stream().map(BaggageEvent::getId).toList());

        // 行李仍在 B，新路线从 B 接续。
        Baggage status = service.currentStatus("BAG030");
        assertThat(status.getCurrentLocationCode()).isEqualTo("B");
        assertThat(status.getCurrentSegmentId()).isNull();
    }

    @Test
    @DisplayName("改签业务号幂等：内容一致返回首次路线，内容变化返回冲突")
    void rebookChangeNoIdempotent() {
        register("BAG031");
        RebookCommand command = new RebookCommand("CHANGE-2",
                List.of(new SegmentCommand("F5", "A", "D")));

        Route first = service.confirmRebooking("BAG031", command);
        Route replay = service.confirmRebooking("BAG031", command);
        assertThat(replay.getId()).isEqualTo(first.getId());
        assertThat(service.routeHistory("BAG031")).hasSize(2);

        assertThatThrownBy(() -> service.confirmRebooking("BAG031", new RebookCommand(
                "CHANGE-2", List.of(new SegmentCommand("F6", "A", "D")))))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("改签后的新路线必须从行李当前位置接续")
    void rebookMustStartAtCurrentPosition() {
        register("BAG032");
        service.recordEvent("BAG032", event("EVT-L", EventType.LOAD, "F1", "A", T0));
        service.recordEvent("BAG032", event("EVT-U", EventType.UNLOAD, "F1", "B", T0.plusSeconds(60)));

        assertThatThrownBy(() -> service.confirmRebooking("BAG032", new RebookCommand(
                "CHANGE-3", List.of(new SegmentCommand("F5", "A", "D")))))
                .isInstanceOf(BusinessException.class);
    }

    // ------------------------------------------------------------ 并发：装载 vs 改签

    @Test
    @DisplayName("并发装载和改签同一行李时只能形成原航段装载或新路线其中一种一致结果")
    void concurrentLoadAndRebookAreSerialized() throws Exception {
        for (int i = 0; i < 5; i++) {
            String tag = "BAG-C" + i;
            register(tag);

            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch start = new CountDownLatch(1);

                Callable<String> loadTask = () -> {
                    ready.countDown();
                    start.await();
                    service.recordEvent(tag, event("EVT-C-" + tag, EventType.LOAD, "F1", "A", T0));
                    return "LOAD";
                };
                Callable<String> rebookTask = () -> {
                    ready.countDown();
                    start.await();
                    // 从 A 出发的新路线：若装载先提交，位置锚点变为 B，该改签必被拒绝。
                    service.confirmRebooking(tag, new RebookCommand(
                            "CHANGE-" + tag, List.of(new SegmentCommand("F9", "A", "D"))));
                    return "REBOOK";
                };

                Future<String> loadFuture = pool.submit(loadTask);
                Future<String> rebookFuture = pool.submit(rebookTask);
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                start.countDown();

                String loadOutcome = resultOrFail(loadFuture);
                String rebookOutcome = resultOrFail(rebookFuture);

                long successes = List.of(loadOutcome, rebookOutcome).stream()
                        .filter("OK"::equals).count();
                assertThat(successes)
                        .as("装载与改签必须恰好一种成功，tag=%s load=%s rebook=%s", tag, loadOutcome, rebookOutcome)
                        .isEqualTo(1);

                Baggage status = service.currentStatus(tag);
                Route activeRoute = service.plannedRoute(tag);
                RouteSegment f1 = segmentByFlight(tag, "F1");

                if ("OK".equals(loadOutcome)) {
                    // 装载成功：行李在 F1 上，旧路线仍生效。
                    assertThat(status.getCurrentSegmentId()).isNotNull();
                    assertThat(f1.getStatus()).isEqualTo(SegmentStatus.LOADED);
                    assertThat(activeRoute.getSource()).isEqualTo(RouteSource.INITIAL);
                    assertThat(status.getCurrentSegmentId()).isEqualTo(f1.getId());
                } else {
                    // 改签成功：F1 已取消且行李绝不可能在该航段上，新路线生效。
                    assertThat(f1.getStatus()).isEqualTo(SegmentStatus.CANCELLED);
                    assertThat(status.getCurrentSegmentId()).isNull();
                    assertThat(status.getCurrentLocationCode()).isEqualTo("A");
                    assertThat(activeRoute.getSource()).isEqualTo(RouteSource.REBOOK);
                }
            } finally {
                pool.shutdownNow();
            }
        }
    }

    private String resultOrFail(Future<String> future) throws Exception {
        try {
            future.get(10, TimeUnit.SECONDS);
            return "OK";
        } catch (Exception e) {
            return "FAILED";
        }
    }

    // ------------------------------------------------------------ 误装

    @Test
    @DisplayName("误装：登记异常卸载事件+接续计划，禁止直接覆盖位置，并形成异常处理链")
    void misloadRegistersExceptionAndRecoveryRoute() {
        register("BAG040");
        service.recordEvent("BAG040", event("EVT-ML", EventType.LOAD, "F1", "A", T0));
        RouteSegment wrongF1 = segmentByFlight("BAG040", "F1");

        BaggageException exception = service.reportMisload("BAG040", new MisloadCommand(
                "EVT-MIS-1", "X", List.of(new SegmentCommand("F7", "X", "C"))));

        assertThat(exception.getType().name()).isEqualTo("MISLOAD");
        assertThat(exception.getWrongSegmentId()).isEqualTo(wrongF1.getId());
        assertThat(exception.getPreviousExceptionId()).isNull();

        // 位置只通过异常卸载事件移动到 X，而不是被直接覆盖。
        Baggage status = service.currentStatus("BAG040");
        assertThat(status.getCurrentLocationCode()).isEqualTo("X");
        assertThat(status.getCurrentSegmentId()).isNull();

        List<BaggageEvent> events = service.eventHistory("BAG040");
        assertThat(events).extracting(BaggageEvent::getType)
                .containsExactly(EventType.LOAD, EventType.EXCEPTION_UNLOAD);
        BaggageEvent exceptionEvent = events.get(1);
        assertThat(exceptionEvent.getLocationCode()).isEqualTo("X");
        assertThat(exceptionEvent.getExternalEventRef()).isEqualTo("EVT-MIS-1");

        // 误装航段作为事实保留为 LOADED；旧路线其余未执行航段取消；接续路线生效。
        assertThat(segmentById(wrongF1.getId()).getStatus()).isEqualTo(SegmentStatus.LOADED);
        assertThat(segmentByFlight("BAG040", "F2").getStatus()).isEqualTo(SegmentStatus.CANCELLED);
        Route activeRoute = service.plannedRoute("BAG040");
        assertThat(activeRoute.getSource()).isEqualTo(RouteSource.MISLOAD_RECOVERY);
        assertThat(activeRoute.getId()).isEqualTo(exception.getRecoveryRoute().getId());
        assertThat(activeRoute.getSegments()).extracting(RouteSegment::getFlightNo)
                .containsExactly("F7");
        assertThat(exception.getExceptionEvent().getId()).isEqualTo(exceptionEvent.getId());
    }

    @Test
    @DisplayName("误装外部事件号幂等：内容一致返回首次异常记录，内容变化返回冲突")
    void misloadIdempotencyAndConflict() {
        register("BAG041");
        service.recordEvent("BAG041", event("EVT-L41", EventType.LOAD, "F1", "A", T0));

        MisloadCommand command = new MisloadCommand("EVT-MIS-2", "X",
                List.of(new SegmentCommand("F7", "X", "C")));
        BaggageException first = service.reportMisload("BAG041", command);
        BaggageException replay = service.reportMisload("BAG041", command);
        assertThat(replay.getId()).isEqualTo(first.getId());

        assertThat(service.exceptionChain("BAG041")).hasSize(1);
        assertThat(service.eventHistory("BAG041")).hasSize(2);

        assertThatThrownBy(() -> service.reportMisload("BAG041", new MisloadCommand(
                "EVT-MIS-2", "Y", List.of(new SegmentCommand("F7", "X", "C")))))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("不在任何航段上时报误装被拒绝")
    void misloadWhileAtLocationRejected() {
        register("BAG042");
        assertThatThrownBy(() -> service.reportMisload("BAG042", new MisloadCommand(
                "EVT-MIS-3", "X", List.of(new SegmentCommand("F7", "X", "C")))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("再次误装时异常记录挂接为处理链，前一异常 id 被记录")
    void repeatedMisloadChainsExceptions() {
        register("BAG043");
        service.recordEvent("BAG043", event("EVT-L43", EventType.LOAD, "F1", "A", T0));
        BaggageException first = service.reportMisload("BAG043", new MisloadCommand(
                "EVT-MIS-A", "X", List.of(new SegmentCommand("F7", "X", "C"))));

        // 在接续路线 F7 上再次误装到 Y。
        service.recordEvent("BAG043", event("EVT-L43B", EventType.LOAD, "F7", "X", T0.plusSeconds(60)));
        BaggageException second = service.reportMisload("BAG043", new MisloadCommand(
                "EVT-MIS-B", "Y", List.of(new SegmentCommand("F8", "Y", "C"))));

        List<BaggageException> chain = service.exceptionChain("BAG043");
        assertThat(chain).hasSize(2);
        assertThat(chain.get(0).getId()).isEqualTo(first.getId());
        assertThat(chain.get(1).getId()).isEqualTo(second.getId());
        assertThat(chain.get(1).getPreviousExceptionId()).isEqualTo(first.getId());
        assertThat(service.currentStatus("BAG043").getCurrentLocationCode()).isEqualTo("Y");
    }

    // ------------------------------------------------------------ 辅助

    private Baggage register(String tag) {
        return service.registerBaggage(new RegisterBaggageCommand(
                tag, "张三", "ITIN-" + tag, "A",
                List.of(new SegmentCommand("F1", "A", "B"),
                        new SegmentCommand("F2", "B", "C"))));
    }

    private RecordEventCommand event(String ref, EventType type, String flightNo,
                                     String locationCode, Instant occurredAt) {
        return new RecordEventCommand(ref, type, flightNo, locationCode, occurredAt);
    }

    private RouteSegment segmentByFlight(String tag, String flightNo) {
        Long baggageId = service.currentStatus(tag).getId();
        return segmentRepository.findByBaggageId(baggageId).stream()
                .filter(segment -> segment.getFlightNo().equals(flightNo))
                .reduce((a, b) -> {
                    throw new IllegalStateException("存在多个航段 " + flightNo);
                })
                .orElseThrow(() -> new IllegalStateException("航段不存在: " + flightNo));
    }

    private RouteSegment segmentById(Long id) {
        return segmentRepository.findById(id).orElseThrow();
    }
}
