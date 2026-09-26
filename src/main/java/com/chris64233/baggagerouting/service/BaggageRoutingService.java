package com.chris64233.baggagerouting.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.chris64233.baggagerouting.domain.Baggage;
import com.chris64233.baggagerouting.domain.BaggageEvent;
import com.chris64233.baggagerouting.domain.BaggageException;
import com.chris64233.baggagerouting.domain.EventType;
import com.chris64233.baggagerouting.domain.ExceptionStatus;
import com.chris64233.baggagerouting.domain.ExceptionType;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 联程行李路由核心服务。
 * <p>
 * 主要业务规则：
 * <ul>
 *   <li>行李在任一时刻只能处于一个明确地点或一个运输航段上（单一位置不变式）。</li>
 *   <li>装载/卸载/交接事件不可变；同一外部事件号重复上报返回首次结果，内容变化返回冲突。</li>
 *   <li>改签确认在同一事务内取消旧路线未执行航段并创建新路线；
 *       已装载/已完成航段与既有交接记录不得改变；改签业务号保证幂等。</li>
 *   <li>写操作统一对行李行加悲观锁，并发装载与改签串行化，只形成一种一致结果。</li>
 *   <li>误装必须登记异常卸载事件并挂接新接续计划，禁止直接覆盖当前位置。</li>
 * </ul>
 */
@Service
public class BaggageRoutingService {

    private final BaggageRepository baggageRepository;
    private final RouteRepository routeRepository;
    private final RouteSegmentRepository segmentRepository;
    private final BaggageEventRepository eventRepository;
    private final BaggageExceptionRepository exceptionRepository;
    private final Clock clock;

    public BaggageRoutingService(BaggageRepository baggageRepository,
                                 RouteRepository routeRepository,
                                 RouteSegmentRepository segmentRepository,
                                 BaggageEventRepository eventRepository,
                                 BaggageExceptionRepository exceptionRepository,
                                 Clock clock) {
        this.baggageRepository = baggageRepository;
        this.routeRepository = routeRepository;
        this.segmentRepository = segmentRepository;
        this.eventRepository = eventRepository;
        this.exceptionRepository = exceptionRepository;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- 登记

    @Transactional
    public Baggage registerBaggage(RegisterBaggageCommand command) {
        baggageRepository.findByTag(command.tag()).ifPresent(existing -> {
            throw new ConflictException("行李标签已存在: " + command.tag());
        });
        validateSegmentChain(command.initialLocation(), command.segments());

        Baggage baggage = baggageRepository.save(new Baggage(
                command.tag(), command.passengerName(), command.itineraryRef(), command.initialLocation()));

        Route route = new Route(baggage, RouteSource.INITIAL, RouteStatus.ACTIVE, null, Instant.now(clock));
        appendSegments(route, command.segments());
        routeRepository.save(route);
        return baggage;
    }

    // ---------------------------------------------------------------- 事件

    /**
     * 记录外部扫描事件。幂等规则：
     * 同一 externalEventRef 且内容一致 → 返回首次登记的事件；内容不一致 → 409 冲突。
     */
    @Transactional
    public BaggageEvent recordEvent(String tag, RecordEventCommand command) {
        if (command.type() == EventType.EXCEPTION_UNLOAD) {
            throw new BusinessException("异常卸载只能通过误装处理接口登记");
        }
        String payloadHash = PayloadHasher.sha256(canonicalPayload(tag, command));

        var existing = eventRepository.findByExternalEventRef(command.externalEventRef());
        if (existing.isPresent()) {
            return resolveDuplicate(existing.get(), payloadHash);
        }

        Baggage baggage = lockBaggage(tag);
        BaggageEvent event = switch (command.type()) {
            case LOAD -> applyLoad(baggage, command, payloadHash);
            case UNLOAD -> applyUnload(baggage, command, payloadHash);
            case HANDOVER -> applyHandover(baggage, command, payloadHash);
            case EXCEPTION_UNLOAD -> throw new BusinessException("异常卸载只能通过误装处理接口登记");
        };
        try {
            return eventRepository.saveAndFlush(event);
        } catch (DataIntegrityViolationException e) {
            // 并发下同一外部事件号撞唯一约束：以首次写入为准。
            return eventRepository.findByExternalEventRef(command.externalEventRef())
                    .map(first -> resolveDuplicate(first, payloadHash))
                    .orElseThrow(() -> e);
        }
    }

    private BaggageEvent applyLoad(Baggage baggage, RecordEventCommand command, String payloadHash) {
        if (baggage.isOnSegment()) {
            throw new BusinessException("行李已在航段 " + baggage.getCurrentSegmentId() + " 上，不能重复装载");
        }
        RouteSegment segment = nextPlannedSegment(baggage);
        if (command.flightNo() == null || !command.flightNo().equals(segment.getFlightNo())) {
            throw new BusinessException("装载航班 " + command.flightNo() + " 与计划航段 " + segment.getFlightNo() + " 不符");
        }
        if (!command.locationCode().equals(segment.getOrigin())) {
            throw new BusinessException("装载地点 " + command.locationCode() + " 与航段起点 " + segment.getOrigin() + " 不符");
        }
        if (!command.locationCode().equals(baggage.getCurrentLocationCode())) {
            throw new BusinessException("行李当前不在 " + command.locationCode() + "，无法在此装载");
        }
        segment.setStatus(SegmentStatus.LOADED);
        baggage.moveToSegment(segment.getId());
        return newEvent(baggage, command, payloadHash);
    }

    private BaggageEvent applyUnload(Baggage baggage, RecordEventCommand command, String payloadHash) {
        if (!baggage.isOnSegment()) {
            throw new BusinessException("行李不在任何航段上，无法卸载");
        }
        RouteSegment segment = segmentRepository.findById(baggage.getCurrentSegmentId())
                .orElseThrow(() -> new IllegalStateException("当前航段不存在: " + baggage.getCurrentSegmentId()));
        if (!command.locationCode().equals(segment.getDestination())) {
            // 在计划外地点发现行李属于误装，必须走误装处理流程，不能按正常卸载覆盖位置。
            throw new BusinessException("卸载地点 " + command.locationCode() + " 与航段终点 "
                    + segment.getDestination() + " 不符，请按误装流程处理");
        }
        segment.setStatus(SegmentStatus.COMPLETED);
        baggage.moveToLocation(segment.getDestination());
        return newEvent(baggage, command, payloadHash);
    }

    private BaggageEvent applyHandover(Baggage baggage, RecordEventCommand command, String payloadHash) {
        if (baggage.isOnSegment()) {
            throw new BusinessException("行李在航段运输途中，无法交接");
        }
        if (!command.locationCode().equals(baggage.getCurrentLocationCode())) {
            throw new BusinessException("交接地点 " + command.locationCode() + " 与行李当前位置 "
                    + baggage.getCurrentLocationCode() + " 不符");
        }
        return newEvent(baggage, command, payloadHash);
    }

    // ---------------------------------------------------------------- 改签

    /**
     * 确认改签：同一事务内取消旧 ACTIVE 路线的全部未执行（PLANNED）航段并创建新路线。
     * 已装载/已完成航段、既有事件与交接记录保持不变。
     * 改签业务号 changeNo 幂等：内容一致返回首次创建的路线，内容不一致返回冲突。
     */
    @Transactional
    public Route confirmRebooking(String tag, RebookCommand command) {
        Baggage baggage = lockBaggage(tag);

        var existing = routeRepository.findByChangeNo(command.changeNo());
        if (existing.isPresent()) {
            Route route = existing.get();
            if (!route.getBaggage().getId().equals(baggage.getId())) {
                throw new ConflictException("改签业务号 " + command.changeNo() + " 已用于其他行李");
            }
            if (!segmentsMatch(route, command.segments())) {
                throw new ConflictException("改签业务号 " + command.changeNo() + " 已存在且航段内容不一致");
            }
            route.getSegments().size();
            return route;
        }

        Route activeRoute = activeRoute(baggage);
        String startPoint = currentPosition(baggage);
        validateSegmentChain(startPoint, command.segments());

        activeRoute.cancelUnloadedSegments();

        Route newRoute = new Route(baggage, RouteSource.REBOOK, RouteStatus.ACTIVE,
                command.changeNo(), Instant.now(clock));
        appendSegments(newRoute, command.segments());
        try {
            return routeRepository.saveAndFlush(newRoute);
        } catch (DataIntegrityViolationException e) {
            // 并发下同一改签业务号撞唯一约束：以首次确认的路线为准。
            Route first = routeRepository.findByChangeNo(command.changeNo())
                    .orElseThrow(() -> e);
            if (!segmentsMatch(first, command.segments())) {
                throw new ConflictException("改签业务号 " + command.changeNo() + " 已存在且航段内容不一致");
            }
            first.getSegments().size();
            return first;
        }
    }

    // ---------------------------------------------------------------- 误装

    /**
     * 误装处理：登记异常卸载（不可变 EXCEPTION_UNLOAD 事件）并创建新的接续计划路线。
     * 通过事件驱动位置变更，禁止直接覆盖当前位置。
     * 同一外部事件号幂等：内容一致返回首次的异常记录，内容不一致返回冲突。
     */
    @Transactional
    public BaggageException reportMisload(String tag, MisloadCommand command) {
        String payloadHash = PayloadHasher.sha256(canonicalMisloadPayload(tag, command));

        var existingEvent = eventRepository.findByExternalEventRef(command.externalEventRef());
        if (existingEvent.isPresent()) {
            BaggageEvent first = existingEvent.get();
            if (!first.getPayloadHash().equals(payloadHash)) {
                throw new ConflictException("外部事件 " + command.externalEventRef() + " 已存在且内容不一致");
            }
            return exceptionRepository.findByExceptionEventId(first.getId())
                    .orElseThrow(() -> new IllegalStateException("异常事件已存在但缺少异常记录: " + first.getId()));
        }

        Baggage baggage = lockBaggage(tag);
        if (!baggage.isOnSegment()) {
            throw new BusinessException("行李不在任何航段上，不构成误装");
        }
        Long wrongSegmentId = baggage.getCurrentSegmentId();

        // 1) 登记异常卸载事件：这是唯一允许把行李从误装航段移出的方式。
        BaggageEvent exceptionEvent = eventRepository.save(new BaggageEvent(
                baggage, EventType.EXCEPTION_UNLOAD, null, command.foundLocationCode(),
                command.externalEventRef(), payloadHash, Instant.now(clock), Instant.now(clock)));
        baggage.moveToLocation(command.foundLocationCode());

        // 2) 取消旧路线未执行航段，挂接新的接续计划。
        Route activeRoute = activeRoute(baggage);
        validateSegmentChain(command.foundLocationCode(), command.segments());
        activeRoute.cancelUnloadedSegments();

        Route recoveryRoute = new Route(baggage, RouteSource.MISLOAD_RECOVERY, RouteStatus.ACTIVE,
                null, Instant.now(clock));
        appendSegments(recoveryRoute, command.segments());
        routeRepository.save(recoveryRoute);

        // 3) 登记异常记录并挂到处理链上。
        Long previousExceptionId = exceptionRepository.findByBaggageIdOrderByIdAsc(baggage.getId())
                .stream().reduce((first, second) -> second).map(BaggageException::getId).orElse(null);
        return exceptionRepository.save(new BaggageException(
                baggage, ExceptionType.MISLOAD, wrongSegmentId, command.foundLocationCode(),
                exceptionEvent, recoveryRoute, previousExceptionId,
                ExceptionStatus.RECOVERY_PLANNED, Instant.now(clock)));
    }

    // ---------------------------------------------------------------- 查询

    @Transactional(readOnly = true)
    public Baggage currentStatus(String tag) {
        return baggageRepository.findByTag(tag)
                .orElseThrow(() -> new NotFoundException("行李不存在: " + tag));
    }

    /** 当前生效的计划路线（ACTIVE）。 */
    @Transactional(readOnly = true)
    public Route plannedRoute(String tag) {
        Baggage baggage = currentStatus(tag);
        Route route = routeRepository.findByBaggageIdAndStatus(baggage.getId(), RouteStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("行李没有生效中的路线: " + tag));
        route.getSegments().size();
        return route;
    }

    /** 全部路线（含已取消），按创建顺序。 */
    @Transactional(readOnly = true)
    public List<Route> routeHistory(String tag) {
        Baggage baggage = currentStatus(tag);
        List<Route> routes = routeRepository.findByBaggageIdOrderByIdAsc(baggage.getId());
        routes.forEach(route -> route.getSegments().size());
        return routes;
    }

    /** 实际发生的不可变事件流。 */
    @Transactional(readOnly = true)
    public List<BaggageEvent> eventHistory(String tag) {
        Baggage baggage = currentStatus(tag);
        return eventRepository.findByBaggageIdOrderByIdAsc(baggage.getId());
    }

    /** 异常处理链，按发生顺序。 */
    @Transactional(readOnly = true)
    public List<BaggageException> exceptionChain(String tag) {
        Baggage baggage = currentStatus(tag);
        return exceptionRepository.findByBaggageIdOrderByIdAsc(baggage.getId());
    }

    // ---------------------------------------------------------------- 内部

    private Baggage lockBaggage(String tag) {
        return baggageRepository.findByTagForUpdate(tag)
                .orElseThrow(() -> new NotFoundException("行李不存在: " + tag));
    }

    private Route activeRoute(Baggage baggage) {
        return routeRepository.findByBaggageIdAndStatus(baggage.getId(), RouteStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException("行李没有生效中的路线: " + baggage.getTag()));
    }

    /** 当前生效路线中顺序最前的未执行航段。 */
    private RouteSegment nextPlannedSegment(Baggage baggage) {
        return activeRoute(baggage).getSegments().stream()
                .filter(s -> s.getStatus() == SegmentStatus.PLANNED)
                .findFirst()
                .orElseThrow(() -> new BusinessException("当前路线没有可执行的计划航段"));
    }

    /** 行李当前的“位置锚点”：在地点则为地点代码；在航段上则为该航段终点。 */
    private String currentPosition(Baggage baggage) {
        if (!baggage.isOnSegment()) {
            return baggage.getCurrentLocationCode();
        }
        return segmentRepository.findById(baggage.getCurrentSegmentId())
                .orElseThrow(() -> new IllegalStateException("当前航段不存在: " + baggage.getCurrentSegmentId()))
                .getDestination();
    }

    /** 航段链必须从 startPoint 出发且首尾相接。 */
    private void validateSegmentChain(String startPoint, List<SegmentCommand> segments) {
        String expectedOrigin = startPoint;
        for (SegmentCommand segment : segments) {
            if (!segment.origin().equals(expectedOrigin)) {
                throw new BusinessException("航段链不连续：期望从 " + expectedOrigin
                        + " 出发，但航段 " + segment.flightNo() + " 起点为 " + segment.origin());
            }
            expectedOrigin = segment.destination();
        }
    }

    private void appendSegments(Route route, List<SegmentCommand> segments) {
        int seq = 1;
        for (SegmentCommand segment : segments) {
            route.addSegment(seq++, segment.flightNo(), segment.origin(), segment.destination(),
                    SegmentStatus.PLANNED);
        }
    }

    private boolean segmentsMatch(Route route, List<SegmentCommand> segments) {
        List<RouteSegment> existing = route.getSegments();
        if (existing.size() != segments.size()) {
            return false;
        }
        for (int i = 0; i < existing.size(); i++) {
            RouteSegment e = existing.get(i);
            SegmentCommand c = segments.get(i);
            if (!Objects.equals(e.getFlightNo(), c.flightNo())
                    || !Objects.equals(e.getOrigin(), c.origin())
                    || !Objects.equals(e.getDestination(), c.destination())) {
                return false;
            }
        }
        return true;
    }

    private BaggageEvent newEvent(Baggage baggage, RecordEventCommand command, String payloadHash) {
        return new BaggageEvent(baggage, command.type(), command.flightNo(), command.locationCode(),
                command.externalEventRef(), payloadHash, command.occurredAt(), Instant.now(clock));
    }

    private BaggageEvent resolveDuplicate(BaggageEvent first, String payloadHash) {
        if (!first.getPayloadHash().equals(payloadHash)) {
            throw new ConflictException("外部事件 " + first.getExternalEventRef() + " 已存在且内容不一致");
        }
        return first;
    }

    private String canonicalPayload(String tag, RecordEventCommand command) {
        return String.join("|", tag, command.type().name(),
                command.flightNo() == null ? "" : command.flightNo(),
                command.locationCode(), command.occurredAt().toString());
    }

    private String canonicalMisloadPayload(String tag, MisloadCommand command) {
        return String.join("|", tag, EventType.EXCEPTION_UNLOAD.name(), "", command.foundLocationCode());
    }
}
