package com.chris64233.baggagerouting.api;

import com.chris64233.baggagerouting.service.BaggageService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 联程行李路由 REST 接口。 */
@RestController
@RequestMapping("/api/baggage")
public class BaggageController {

    private final BaggageService baggageService;

    public BaggageController(BaggageService baggageService) {
        this.baggageService = baggageService;
    }

    /** 建行李：唯一标签 + 旅客行程 + 顺序航段。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BaggageStatusResponse create(@Valid @RequestBody CreateBaggageRequest request) {
        return baggageService.createBaggage(request);
    }

    /** 行李当前状态。 */
    @GetMapping("/{tag}")
    public BaggageStatusResponse status(@PathVariable String tag) {
        return baggageService.currentStatus(tag);
    }

    /** 计划路线（含全部历史世代与当前有效世代）。 */
    @GetMapping("/{tag}/route")
    public RoutePlanResponse route(@PathVariable String tag) {
        return baggageService.plannedRoute(tag);
    }

    /** 实际事件（不可变的装载/卸载/交接/异常卸载流水）。 */
    @GetMapping("/{tag}/events")
    public EventsResponse events(@PathVariable String tag) {
        return new EventsResponse(tag, baggageService.actualEvents(tag));
    }

    /** 异常处理链（每次误装及其接续计划）。 */
    @GetMapping("/{tag}/exceptions")
    public ExceptionChainResponse exceptions(@PathVariable String tag) {
        return baggageService.exceptionChain(tag);
    }

    /** 接收外部扫描：装载 / 卸载 / 交接（externalEventId 幂等）。 */
    @PostMapping("/{tag}/scan")
    public ActionResultResponse scan(@PathVariable String tag,
                                     @Valid @RequestBody ScanRequest request) {
        return baggageService.scan(tag, request);
    }

    /** 确认改签：改签业务号幂等，原子取消旧未执行路由并创建新路线。 */
    @PostMapping("/{tag}/rebook")
    public ActionResultResponse rebook(@PathVariable String tag,
                                       @Valid @RequestBody RebookRequest request) {
        return baggageService.rebook(tag, request);
    }

    /** 误装登记：异常卸载 + 新接续计划，不允许直接覆盖当前位置。 */
    @PostMapping("/{tag}/exceptions")
    public ResponseEntity<ActionResultResponse> registerException(
            @PathVariable String tag,
            @Valid @RequestBody RegisterExceptionRequest request) {
        ActionResultResponse response = baggageService.registerException(tag, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** 实际事件列表包装。 */
    public record EventsResponse(String tag, List<EventView> events) {
    }
}
