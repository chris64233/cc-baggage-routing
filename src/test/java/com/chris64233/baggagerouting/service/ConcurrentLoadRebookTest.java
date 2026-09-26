package com.chris64233.baggagerouting.service;

import static com.chris64233.baggagerouting.TestData.PEK;
import static com.chris64233.baggagerouting.TestData.PVG;
import static com.chris64233.baggagerouting.TestData.SZX;
import static com.chris64233.baggagerouting.TestData.defaultRequest;
import static com.chris64233.baggagerouting.TestData.seg;
import static org.assertj.core.api.Assertions.assertThat;

import com.chris64233.baggagerouting.api.ActionResultResponse;
import com.chris64233.baggagerouting.api.BaggageStatusResponse;
import com.chris64233.baggagerouting.api.EventView;
import com.chris64233.baggagerouting.api.RebookRequest;
import com.chris64233.baggagerouting.api.ScanRequest;
import com.chris64233.baggagerouting.api.SegmentPlan;
import java.util.List;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 并发不变量：同一行李并发装载与改签，只能形成“原航段装载”或“新路线”其中一种结果，
 * 行李绝不可能同时出现在两条路线上（不会既产生 LOAD 事件又产生新一代路线）。
 */
@SpringBootTest
class ConcurrentLoadRebookTest {

    @Autowired
    private BaggageService baggageService;

    private record RaceOutcome(boolean loadWon, boolean rebookWon) {
    }

    /** 一轮装载/改签竞争，返回双方是否成功（理论上必须恰好一方成功）。 */
    private RaceOutcome runOnce(String tag) throws Exception {
        baggageService.createBaggage(defaultRequest(tag));

        // 改签把起点 PEK 的所有后续航段换成不同航班 MU0001，
        // 因此若改签先生效，CA1501 的装载必然因“计划航班不符”失败；反之改签会因在途失败。
        List<SegmentPlan> newRoute = List.of(seg("MU", "MU0001", PEK, PVG), seg("MU", "MU0002", PVG, SZX));

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> loadFuture = pool.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                try {
                    ActionResultResponse r = baggageService.scan(tag,
                            new ScanRequest("EV-LOAD-" + tag, "LOAD", "CA1501", PEK, null, null));
                    return r.action().equals("LOAD") && !r.replayed();
                } catch (RuntimeException e) {
                    return false;
                }
            });
            Future<Boolean> rebookFuture = pool.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                try {
                    ActionResultResponse r = baggageService.rebook(tag,
                            new RebookRequest("RB-" + tag, newRoute, "并发改签"));
                    return r.action().equals("REBOOK") && !r.replayed();
                } catch (RuntimeException e) {
                    return false;
                }
            });
            boolean loadWon = loadFuture.get(30, TimeUnit.SECONDS);
            boolean rebookWon = rebookFuture.get(30, TimeUnit.SECONDS);
            return new RaceOutcome(loadWon, rebookWon);
        } finally {
            pool.shutdownNow();
        }
    }

    @RepeatedTest(30)
    void concurrentLoadAndRebookProduceExactlyOneWinner() throws Exception {
        String tag = "TAG-RACE-" + System.nanoTime();
        RaceOutcome outcome = runOnce(tag);

        // 核心 XOR：恰好一种结果
        assertThat(outcome.loadWon())
                .as("装载与改签必须恰好一方成功: %s", outcome)
                .isNotEqualTo(outcome.rebookWon());

        BaggageStatusResponse state = baggageService.currentStatus(tag);
        List<EventView> events = baggageService.actualEvents(tag);
        int generations = baggageService.plannedRoute(tag).generations().size();
        boolean loadEventExists = events.stream().anyMatch(e -> e.type().equals("LOAD"));

        if (outcome.loadWon()) {
            // 原航段装载赢：行李在 CA1501 上，仍是第 1 代路线，无新世代
            assertThat(state.state().status()).isEqualTo("ON_SEGMENT");
            assertThat(state.state().flightNumber()).isEqualTo("CA1501");
            assertThat(state.state().activeGenerationNo()).isEqualTo(1);
            assertThat(generations).isEqualTo(1);
            assertThat(loadEventExists).isTrue();
        } else {
            // 改签赢：行李仍在 PEK 地面，第 2 代路线生效，没有任何装载事件
            assertThat(state.state().status()).isEqualTo("AT_LOCATION");
            assertThat(state.state().location()).isEqualTo(PEK);
            assertThat(state.state().activeGenerationNo()).isEqualTo(2);
            assertThat(generations).isEqualTo(2);
            assertThat(loadEventExists)
                    .as("改签生效后不得残留旧航段装载事件，行李不能同时出现在两条路线")
                    .isFalse();
        }
    }

    @Test
    void concurrentDuplicateExternalEventProcessedOnce() throws Exception {
        String tag = "TAG-DUP-RACE";
        baggageService.createBaggage(defaultRequest(tag));

        int threads = 8;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger processed = new AtomicInteger();
        AtomicInteger replayed = new AtomicInteger();
        try {
            List<Future<Boolean>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    ActionResultResponse r = baggageService.scan(tag,
                            new ScanRequest("EV-SAME", "LOAD", "CA1501", PEK, null, null));
                    return !r.replayed();
                }));
            }
            for (Future<Boolean> f : futures) {
                if (f.get(30, TimeUnit.SECONDS)) {
                    processed.incrementAndGet();
                } else {
                    replayed.incrementAndGet();
                }
            }
        } finally {
            pool.shutdownNow();
        }

        // 恰好一次真实处理，其余全部重放，事件流水只有一条
        assertThat(processed.get()).isEqualTo(1);
        assertThat(replayed.get()).isEqualTo(threads - 1);
        assertThat(baggageService.actualEvents(tag)).hasSize(1);
        assertThat(baggageService.currentStatus(tag).state().status()).isEqualTo("ON_SEGMENT");
    }
}
