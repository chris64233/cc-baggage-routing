package com.chris64233.baggagerouting.service;

import com.chris64233.baggagerouting.api.ActionResultResponse;
import com.chris64233.baggagerouting.api.BaggageStateView;
import com.chris64233.baggagerouting.api.BaggageStatusResponse;
import com.chris64233.baggagerouting.api.CreateBaggageRequest;
import com.chris64233.baggagerouting.api.EventView;
import com.chris64233.baggagerouting.api.ExceptionChainResponse;
import com.chris64233.baggagerouting.api.RebookRequest;
import com.chris64233.baggagerouting.api.RegisterExceptionRequest;
import com.chris64233.baggagerouting.api.RoutePlanResponse;
import com.chris64233.baggagerouting.api.ScanRequest;
import com.chris64233.baggagerouting.api.SegmentPlan;
import com.chris64233.baggagerouting.domain.Baggage;
import com.chris64233.baggagerouting.domain.BaggageEvent;
import com.chris64233.baggagerouting.domain.BaggageStatus;
import com.chris64233.baggagerouting.domain.EventType;
import com.chris64233.baggagerouting.domain.ExceptionCase;
import com.chris64233.baggagerouting.domain.ExternalEventRecord;
import com.chris64233.baggagerouting.domain.GenerationStatus;
import com.chris64233.baggagerouting.domain.RebookOrder;
import com.chris64233.baggagerouting.domain.RouteGeneration;
import com.chris64233.baggagerouting.domain.RouteSegment;
import com.chris64233.baggagerouting.domain.SegmentStatus;
import com.chris64233.baggagerouting.repository.BaggageEventRepository;
import com.chris64233.baggagerouting.repository.BaggageRepository;
import com.chris64233.baggagerouting.repository.ExceptionCaseRepository;
import com.chris64233.baggagerouting.repository.ExternalEventRecordRepository;
import com.chris64233.baggagerouting.repository.RebookOrderRepository;
import com.chris64233.baggagerouting.repository.RouteGenerationRepository;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 行李路由核心领域服务。
 *
 * <p>并发模型：所有写操作先对行李行加悲观写锁（{@code findByTagForUpdate}），
 * 同一行李的装载与改签因此被数据库串行化，配合世代状态机保证“原航段装载”和
 * “新路线”两种结果最多发生一种（XOR），行李不可能同时出现在两条路线上。
 */
@Service
public class BaggageService {

    private static final Logger log = LoggerFactory.getLogger(BaggageService.class);

    private final BaggageRepository baggageRepository;
    private final RouteGenerationRepository generationRepository;
    private final BaggageEventRepository eventRepository;
    private final ExternalEventRecordRepository externalEventRepository;
    private final RebookOrderRepository rebookOrderRepository;
    private final ExceptionCaseRepository exceptionCaseRepository;
    private final BaggageViewMapper mapper;
    private final ObjectMapper objectMapper;

    public BaggageService(BaggageRepository baggageRepository,
                          RouteGenerationRepository generationRepository,
                          BaggageEventRepository eventRepository,
                          ExternalEventRecordRepository externalEventRepository,
                          RebookOrderRepository rebookOrderRepository,
                          ExceptionCaseRepository exceptionCaseRepository,
                          BaggageViewMapper mapper,
                          ObjectMapper objectMapper) {
        this.baggageRepository = baggageRepository;
        this.generationRepository = generationRepository;
        this.eventRepository = eventRepository;
        this.externalEventRepository = externalEventRepository;
        this.rebookOrderRepository = rebookOrderRepository;
        this.exceptionCaseRepository = exceptionCaseRepository;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------
    // 建行李
    // ------------------------------------------------------------------

    @Transactional
    public BaggageStatusResponse createBaggage(CreateBaggageRequest request) {
        if (baggageRepository.existsByTag(request.tag())) {
            throw new ConflictException("行李标签已存在: " + request.tag());
        }
        List<SegmentPlan> plans = request.segments();
        String startAt = plans.get(0).origin();
        // 行程目的地以顺序航段链的终点为准
        String finalDestination = RouteFactory.lastDestination(plans);
        Baggage baggage = new Baggage(request.tag(), request.passengerItinerary(),
                request.passengerName(), startAt, finalDestination);
        try {
            baggage = baggageRepository.saveAndFlush(baggage);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("行李标签已存在: " + request.tag());
        }
        RouteGeneration generation = RouteGeneration.initial(baggage, 1, startAt, finalDestination);
        RouteFactory.appendSegments(generation, plans, startAt);
        baggage.setActiveGeneration(generation);
        generationRepository.save(generation);
        return mapper.status(baggage);
    }

    // ------------------------------------------------------------------
    // 扫描事件：装载 / 卸载 / 交接（外部事件幂等）
    // ------------------------------------------------------------------

    @Transactional
    public ActionResultResponse scan(String tag, ScanRequest request) {
        String payloadHash = scanHash(tag, request);
        // 锁外快速重放：已处理过的外部事件直接返回首次结果
        ActionResultResponse replay = replayExternalIfPresent(request.externalEventId(), payloadHash);
        if (replay != null) {
            return replay;
        }
        Baggage baggage = lockBaggage(tag);
        // 取锁后二次检查，防止两个相同事件并发排队时重复执行
        replay = replayExternalIfPresent(request.externalEventId(), payloadHash);
        if (replay != null) {
            return replay;
        }

        EventType type = parseEventType(request.type());
        Instant now = Instant.now();
        ActionResultResponse result = switch (type) {
            case LOAD -> doLoad(baggage, request, now);
            case UNLOAD -> doUnload(baggage, request, now);
            case HANDOVER -> doHandover(baggage, request, now);
            default -> throw new BusinessRuleException("扫描类型不支持: " + request.type());
        };
        rememberExternal(request.externalEventId(), baggage, payloadHash, result);
        return result;
    }

    private ActionResultResponse doLoad(Baggage baggage, ScanRequest request, Instant now) {
        if (baggage.getStatus() != BaggageStatus.AT_LOCATION) {
            throw new BusinessRuleException("行李正在航段 "
                    + baggage.getCurrentSegment().getFlightNumber() + " 上，不能重复装载");
        }
        RouteGeneration active = baggage.getActiveGeneration();
        RouteSegment next = active.getSegments().stream()
                .filter(s -> s.getStatus() == SegmentStatus.PLANNED)
                .min((a, b) -> Integer.compare(a.getSegmentNo(), b.getSegmentNo()))
                .orElseThrow(() -> new BusinessRuleException("当前路线已无未执行航段，无法装载"));

        if (request.flightNumber() == null || request.flightNumber().isBlank()) {
            throw new BusinessRuleException("装载扫描必须提供实际航班号");
        }
        // 扫描航班与计划不符即误装：拒绝装载并提示走异常登记流程
        if (!request.flightNumber().equals(next.getFlightNumber())) {
            throw new BusinessRuleException(
                    "扫描航班 " + request.flightNumber() + " 与计划航段 " + next.getFlightNumber()
                            + " 不符，疑似误装，请先卸载并登记异常，禁止装载到错误航段");
        }
        String expectedLocation = next.getOrigin();
        if (request.location() != null && !request.location().isBlank()
                && !request.location().equals(expectedLocation)) {
            throw new BusinessRuleException("装载地点 " + request.location()
                    + " 与航段起点 " + expectedLocation + " 不一致");
        }
        if (!baggage.getCurrentLocation().equals(expectedLocation)) {
            throw new BusinessRuleException("行李当前位置 " + baggage.getCurrentLocation()
                    + " 与航段起点 " + expectedLocation + " 不一致，无法装载");
        }

        next.markInProgress();
        baggage.moveOnto(next);
        BaggageEvent event = appendEvent(baggage,
                BaggageEvent.load(baggage, nextSeq(baggage), now, request.externalEventId(), next));
        log.info("行李 {} 装载到世代{}航段{} {}", baggage.getTag(),
                active.getGenerationNo(), next.getSegmentNo(), next.getFlightNumber());
        return actionResult("LOAD", event.getExternalEventId(), event.getEventSeq(),
                null, "已装载到 " + next.getFlightNumber(), baggage);
    }

    private ActionResultResponse doUnload(Baggage baggage, ScanRequest request, Instant now) {
        if (baggage.getStatus() != BaggageStatus.ON_SEGMENT) {
            throw new BusinessRuleException("行李不在任何航段上，无法卸载");
        }
        RouteSegment segment = baggage.getCurrentSegment();
        String location = request.location();
        if (location == null || location.isBlank()) {
            location = segment.getDestination();
        }
        if (!location.equals(segment.getDestination())) {
            throw new BusinessRuleException("卸载地点 " + location + " 与计划到达地 "
                    + segment.getDestination() + " 不符，疑似误装，请登记异常而非普通卸载");
        }
        if (request.flightNumber() != null && !request.flightNumber().isBlank()
                && !request.flightNumber().equals(segment.getFlightNumber())) {
            throw new BusinessRuleException("扫描航班 " + request.flightNumber()
                    + " 与行李所在航段 " + segment.getFlightNumber() + " 不符");
        }
        baggage.moveToLocation(location);
        segment.markCompleted();
        BaggageEvent event = appendEvent(baggage,
                BaggageEvent.unload(baggage, nextSeq(baggage), now,
                        request.externalEventId(), segment, location));
        log.info("行李 {} 从 {} 卸载至 {}", baggage.getTag(), segment.getFlightNumber(), location);
        return actionResult("UNLOAD", event.getExternalEventId(), event.getEventSeq(),
                null, "已卸载到 " + location, baggage);
    }

    private ActionResultResponse doHandover(Baggage baggage, ScanRequest request, Instant now) {
        if (request.fromParty() == null || request.fromParty().isBlank()
                || request.toParty() == null || request.toParty().isBlank()) {
            throw new BusinessRuleException("交接事件必须提供交出方和接收方");
        }
        String location = request.location();
        if (location == null || location.isBlank()) {
            if (baggage.getStatus() != BaggageStatus.AT_LOCATION) {
                throw new BusinessRuleException("在途交接必须提供地点");
            }
            location = baggage.getCurrentLocation();
        }
        if (baggage.getStatus() == BaggageStatus.AT_LOCATION
                && !location.equals(baggage.getCurrentLocation())) {
            throw new BusinessRuleException("交接地点 " + location + " 与行李当前位置 "
                    + baggage.getCurrentLocation() + " 不一致");
        }
        BaggageEvent event = appendEvent(baggage,
                BaggageEvent.handover(baggage, nextSeq(baggage), now,
                        request.externalEventId(), location, request.fromParty(), request.toParty()));
        log.info("行李 {} 交接 {} -> {} @ {}", baggage.getTag(),
                request.fromParty(), request.toParty(), location);
        return actionResult("HANDOVER", event.getExternalEventId(), event.getEventSeq(),
                null, "已交接 " + request.fromParty() + " -> " + request.toParty(), baggage);
    }

    // ------------------------------------------------------------------
    // 改签：原子取消旧未执行路由 + 创建新世代
    // ------------------------------------------------------------------

    @Transactional
    public ActionResultResponse rebook(String tag, RebookRequest request) {
        String payloadHash = rebookHash(tag, request);
        ActionResultResponse replay = replayRebookIfPresent(request.orderNo(), payloadHash);
        if (replay != null) {
            return replay;
        }
        Baggage baggage = lockBaggage(tag);
        replay = replayRebookIfPresent(request.orderNo(), payloadHash);
        if (replay != null) {
            return replay;
        }

        // 仅允许为“尚未装载的后续航段”改签：行李必须在地面（已完成航段的末端）
        if (baggage.getStatus() != BaggageStatus.AT_LOCATION) {
            throw new BusinessRuleException(
                    "行李正在航段上，须完成当前航段卸载后才能改签后续路线");
        }
        RouteGeneration oldGeneration = baggage.getActiveGeneration();
        List<RouteSegment> planned = oldGeneration.getSegments().stream()
                .filter(s -> s.getStatus() == SegmentStatus.PLANNED)
                .sorted((a, b) -> Integer.compare(a.getSegmentNo(), b.getSegmentNo()))
                .toList();
        if (planned.isEmpty()) {
            throw new BusinessRuleException("当前路线已无尚未装载的后续航段，无需改签");
        }
        String rerouteFrom = baggage.getCurrentLocation();

        // 一次性取消旧的未执行路由（已完成/执行中的航段不动；这里能执行到说明无执行中航段）
        for (RouteSegment segment : planned) {
            segment.markSuperseded();
        }
        oldGeneration.markSuperseded();

        int newNo = generationRepository.findMaxGenerationNo(baggage.getId()) + 1;
        String destination = RouteFactory.lastDestination(request.segments());
        if (!destination.equals(baggage.getFinalDestination())) {
            throw new BusinessRuleException("改签路线终点 " + destination
                    + " 必须与旅客行程目的地 " + baggage.getFinalDestination() + " 一致");
        }
        RouteGeneration newGeneration = RouteGeneration.rebooked(
                baggage, newNo, rerouteFrom, destination, request.orderNo());
        RouteFactory.appendSegments(newGeneration, request.segments(), rerouteFrom);
        baggage.setActiveGeneration(newGeneration);
        generationRepository.save(newGeneration);

        ActionResultResponse result = actionResult("REBOOK", request.orderNo(), null,
                newNo, "已确认改签，旧未执行航段取消，启用第 " + newNo + " 代路线", baggage);
        rememberRebook(request.orderNo(), baggage, payloadHash, result);
        log.info("行李 {} 改签 {}：世代{} -> 世代{}（起点 {}）", baggage.getTag(),
                request.orderNo(), oldGeneration.getGenerationNo(), newNo, rerouteFrom);
        return result;
    }

    // ------------------------------------------------------------------
    // 误装：登记异常卸载 + 新接续计划（禁止直接覆盖位置）
    // ------------------------------------------------------------------

    @Transactional
    public ActionResultResponse registerException(String tag, RegisterExceptionRequest request) {
        String externalId = request.externalEventId();
        String payloadHash = exceptionHash(tag, request);
        if (externalId != null && !externalId.isBlank()) {
            ActionResultResponse replay = replayExternalIfPresent(externalId, payloadHash);
            if (replay != null) {
                return replay;
            }
        }
        Baggage baggage = lockBaggage(tag);
        if (externalId != null && !externalId.isBlank()) {
            ActionResultResponse replay = replayExternalIfPresent(externalId, payloadHash);
            if (replay != null) {
                return replay;
            }
        }

        if (baggage.getStatus() != BaggageStatus.ON_SEGMENT) {
            throw new BusinessRuleException(
                    "误装登记要求行李正处于运输航段上；地面行李请直接改签后续路线");
        }
        RouteSegment abortedSegment = baggage.getCurrentSegment();
        if (request.actualFlightNumber().equals(abortedSegment.getFlightNumber())) {
            throw new BusinessRuleException(
                    "实际航班与计划航段一致，不构成误装；如需改道请先正常卸载再改签");
        }
        String foundAt = request.foundAtLocation();
        Instant now = Instant.now();

        // 1) 追加异常卸载事件：通过合法状态迁移把行李从误装航段移到发现地点，
        //    绝不直接写 currentLocation 覆盖位置
        baggage.moveToLocation(foundAt);
        abortedSegment.markAborted();
        RouteGeneration oldGeneration = abortedSegment.getGeneration();

        // 2) 同世代内其余未执行航段因误装不再可达，全部取消（已完成航段保持不变）
        List<RouteSegment> remainingPlanned = oldGeneration.getSegments().stream()
                .filter(s -> s.getStatus() == SegmentStatus.PLANNED
                        && s.getSegmentNo() > abortedSegment.getSegmentNo())
                .sorted((a, b) -> Integer.compare(a.getSegmentNo(), b.getSegmentNo()))
                .toList();
        for (RouteSegment s : remainingPlanned) {
            s.markCancelled();
        }
        oldGeneration.markAborted();

        int exceptionCount = exceptionCaseRepository.findByBaggageIdOrderByRegisteredAtAsc(
                baggage.getId()).size();
        String caseNo = String.format("%s-EX-%03d", baggage.getTag(), exceptionCount + 1);
        String previousCaseNo = exceptionCaseRepository
                .findFirstByBaggageIdOrderByRegisteredAtDesc(baggage.getId())
                .map(ExceptionCase::getCaseNo)
                .orElse(null);

        // 3) 生成接续世代（异常处理链的新计划），终点必须仍是行程目的地
        int newNo = generationRepository.findMaxGenerationNo(baggage.getId()) + 1;
        String destination = RouteFactory.lastDestination(request.continuationSegments());
        if (!destination.equals(baggage.getFinalDestination())) {
            throw new BusinessRuleException("接续路线终点 " + destination
                    + " 必须与旅客行程目的地 " + baggage.getFinalDestination() + " 一致");
        }
        RouteGeneration continuation = RouteGeneration.continuation(
                baggage, newNo, foundAt, destination, caseNo);
        RouteFactory.appendSegments(continuation, request.continuationSegments(), foundAt);
        baggage.setActiveGeneration(continuation);
        generationRepository.save(continuation);

        BaggageEvent event = appendEvent(baggage,
                BaggageEvent.exceptionUnload(baggage, nextSeq(baggage), now, externalId,
                        abortedSegment, foundAt, caseNo, request.reason()));

        ExceptionCase exceptionCase = new ExceptionCase(
                caseNo, baggage, abortedSegment, foundAt, request.actualFlightNumber(),
                request.reason(), continuation, previousCaseNo, externalId, now);
        exceptionCaseRepository.save(exceptionCase);

        ActionResultResponse result = actionResult("EXCEPTION_UNLOAD", caseNo, event.getEventSeq(),
                newNo, "已登记异常卸载 (" + caseNo + ")，行李在 " + foundAt
                        + "，启用第 " + newNo + " 代接续路线", baggage);
        if (externalId != null && !externalId.isBlank()) {
            rememberExternal(externalId, baggage, payloadHash, result);
        }
        log.info("行李 {} 误装：计划{}实际{}，异常{}，接续世代{}", baggage.getTag(),
                abortedSegment.getFlightNumber(), request.actualFlightNumber(), caseNo, newNo);
        return result;
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public BaggageStatusResponse currentStatus(String tag) {
        return mapper.status(requireBaggage(tag));
    }

    @Transactional(readOnly = true)
    public RoutePlanResponse plannedRoute(String tag) {
        Baggage baggage = requireBaggage(tag);
        List<RouteGeneration> generations =
                generationRepository.findByBaggageIdOrderByGenerationNoAsc(baggage.getId());
        return mapper.routePlan(baggage, generations);
    }

    @Transactional(readOnly = true)
    public List<EventView> actualEvents(String tag) {
        Baggage baggage = requireBaggage(tag);
        return eventRepository.findByBaggageIdOrderByEventSeqAsc(baggage.getId()).stream()
                .map(mapper::eventView)
                .toList();
    }

    @Transactional(readOnly = true)
    public ExceptionChainResponse exceptionChain(String tag) {
        Baggage baggage = requireBaggage(tag);
        List<ExceptionCase> cases =
                exceptionCaseRepository.findByBaggageIdOrderByRegisteredAtAsc(baggage.getId());
        return mapper.exceptionChain(baggage, cases);
    }

    // ------------------------------------------------------------------
    // 内部辅助
    // ------------------------------------------------------------------

    private Baggage requireBaggage(String tag) {
        return baggageRepository.findByTag(tag)
                .orElseThrow(() -> new NotFoundException("行李不存在: " + tag));
    }

    private Baggage lockBaggage(String tag) {
        return baggageRepository.findByTagForUpdate(tag)
                .orElseThrow(() -> new NotFoundException("行李不存在: " + tag));
    }

    private int nextSeq(Baggage baggage) {
        return baggage.getEvents().size() + 1;
    }

    private BaggageEvent appendEvent(Baggage baggage, BaggageEvent event) {
        baggage.attachEvent(event);
        return eventRepository.save(event);
    }

    private ActionResultResponse actionResult(String action, String referenceId, Integer eventSeq,
                                              Integer generationNo, String message, Baggage baggage) {
        BaggageStateView state = mapper.state(baggage);
        return new ActionResultResponse(false, action, referenceId, eventSeq,
                generationNo, message, state);
    }

    private EventType parseEventType(String raw) {
        try {
            return EventType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("未知扫描类型: " + raw);
        }
    }

    // -- 外部事件幂等 ----------------------------------------------------

    private ActionResultResponse replayExternalIfPresent(String externalEventId, String payloadHash) {
        return externalEventRepository.findById(externalEventId)
                .map(record -> {
                    if (!record.getPayloadHash().equals(payloadHash)) {
                        throw new ConflictException(
                                "外部事件 " + externalEventId + " 重复提交但内容与首次不同，拒绝处理");
                    }
                    return asReplay(record.getResultJson());
                })
                .orElse(null);
    }

    private void rememberExternal(String externalEventId, Baggage baggage, String payloadHash,
                                  ActionResultResponse result) {
        externalEventRepository.save(new ExternalEventRecord(
                externalEventId, baggage, payloadHash, Instant.now(), toJson(result)));
    }

    // -- 改签业务号幂等 --------------------------------------------------

    private ActionResultResponse replayRebookIfPresent(String orderNo, String payloadHash) {
        return rebookOrderRepository.findById(orderNo)
                .map(order -> {
                    if (!order.getPayloadHash().equals(payloadHash)) {
                        throw new ConflictException(
                                "改签业务号 " + orderNo + " 重复提交但路线内容与首次不同，拒绝处理");
                    }
                    return asReplay(order.getResultJson());
                })
                .orElse(null);
    }

    private void rememberRebook(String orderNo, Baggage baggage, String payloadHash,
                                ActionResultResponse result) {
        rebookOrderRepository.save(new RebookOrder(
                orderNo, baggage, payloadHash, Instant.now(), toJson(result)));
    }

    private ActionResultResponse asReplay(String json) {
        try {
            ActionResultResponse first = objectMapper.readValue(json, ActionResultResponse.class);
            return new ActionResultResponse(true, first.action(), first.referenceId(),
                    first.eventSeq(), first.generationNo(),
                    "重复请求，返回首次处理结果", first.state());
        } catch (Exception e) {
            throw new IllegalStateException("无法还原首次处理结果", e);
        }
    }

    private String toJson(ActionResultResponse result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            throw new IllegalStateException("无法序列化处理结果", e);
        }
    }

    // -- 内容指纹 --------------------------------------------------------

    private String scanHash(String tag, ScanRequest request) {
        return PayloadHasher.sha256(tag, nullToEmpty(request.externalEventId()),
                nullToEmpty(request.type()), nullToEmpty(request.flightNumber()),
                nullToEmpty(request.location()), nullToEmpty(request.fromParty()),
                nullToEmpty(request.toParty()));
    }

    private String rebookHash(String tag, RebookRequest request) {
        return PayloadHasher.sha256(tag, nullToEmpty(request.orderNo()),
                nullToEmpty(request.reason()), segmentsHash(request.segments()));
    }

    private String exceptionHash(String tag, RegisterExceptionRequest request) {
        return PayloadHasher.sha256(tag, nullToEmpty(request.externalEventId()),
                nullToEmpty(request.actualFlightNumber()), nullToEmpty(request.foundAtLocation()),
                nullToEmpty(request.reason()), segmentsHash(request.continuationSegments()));
    }

    private String segmentsHash(List<SegmentPlan> segments) {
        StringBuilder sb = new StringBuilder();
        for (SegmentPlan s : segments) {
            sb.append('[').append(s.transportType()).append('>')
                    .append(s.carrier()).append('>').append(s.flightNumber()).append('>')
                    .append(s.origin()).append('>').append(s.destination()).append(']');
        }
        return sb.toString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
