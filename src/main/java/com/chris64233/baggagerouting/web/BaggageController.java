package com.chris64233.baggagerouting.web;

import java.net.URI;
import java.util.List;

import com.chris64233.baggagerouting.service.BaggageRoutingService;
import com.chris64233.baggagerouting.service.dto.MisloadCommand;
import com.chris64233.baggagerouting.service.dto.RebookCommand;
import com.chris64233.baggagerouting.service.dto.RecordEventCommand;
import com.chris64233.baggagerouting.service.dto.RegisterBaggageCommand;
import com.chris64233.baggagerouting.web.dto.BaggageStatusResponse;
import com.chris64233.baggagerouting.web.dto.EventResponse;
import com.chris64233.baggagerouting.web.dto.ExceptionResponse;
import com.chris64233.baggagerouting.web.dto.RouteResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/baggage")
public class BaggageController {

    private final BaggageRoutingService service;

    public BaggageController(BaggageRoutingService service) {
        this.service = service;
    }

    /** 登记行李：唯一标签 + 旅客行程 + 按顺序排列的初始航段。 */
    @PostMapping
    public ResponseEntity<BaggageStatusResponse> register(@Valid @RequestBody RegisterBaggageCommand command) {
        var baggage = service.registerBaggage(command);
        return ResponseEntity
                .created(URI.create("/api/baggage/" + baggage.getTag()))
                .body(BaggageStatusResponse.from(baggage));
    }

    /** 记录装载/卸载/交接事件（幂等：同一外部事件号返回首次结果，内容变化返回 409）。 */
    @PostMapping("/{tag}/events")
    public EventResponse recordEvent(@PathVariable String tag,
                                     @Valid @RequestBody RecordEventCommand command) {
        return EventResponse.from(service.recordEvent(tag, command));
    }

    /** 确认改签：原子取消旧的未执行路由并创建新路线（改签业务号幂等）。 */
    @PostMapping("/{tag}/rebooking")
    public RouteResponse confirmRebooking(@PathVariable String tag,
                                          @Valid @RequestBody RebookCommand command) {
        return RouteResponse.from(service.confirmRebooking(tag, command));
    }

    /** 误装上报：登记异常卸载 + 新的接续计划。 */
    @PostMapping("/{tag}/misload")
    public ExceptionResponse reportMisload(@PathVariable String tag,
                                           @Valid @RequestBody MisloadCommand command) {
        return ExceptionResponse.from(service.reportMisload(tag, command));
    }

    /** 行李当前状态（明确地点或运输航段）。 */
    @GetMapping("/{tag}")
    public BaggageStatusResponse currentStatus(@PathVariable String tag) {
        return BaggageStatusResponse.from(service.currentStatus(tag));
    }

    /** 当前生效的计划路线。 */
    @GetMapping("/{tag}/route")
    public RouteResponse plannedRoute(@PathVariable String tag) {
        return RouteResponse.from(service.plannedRoute(tag));
    }

    /** 全部路线历史（含已取消）。 */
    @GetMapping("/{tag}/routes")
    public List<RouteResponse> routeHistory(@PathVariable String tag) {
        return service.routeHistory(tag).stream().map(RouteResponse::from).toList();
    }

    /** 实际发生的不可变事件流。 */
    @GetMapping("/{tag}/events")
    public List<EventResponse> eventHistory(@PathVariable String tag) {
        return service.eventHistory(tag).stream().map(EventResponse::from).toList();
    }

    /** 异常处理链。 */
    @GetMapping("/{tag}/exceptions")
    public List<ExceptionResponse> exceptionChain(@PathVariable String tag) {
        return service.exceptionChain(tag).stream().map(ExceptionResponse::from).toList();
    }
}
