package com.chris64233.baggagerouting.service;

import com.chris64233.baggagerouting.api.BaggageStateView;
import com.chris64233.baggagerouting.api.BaggageStatusResponse;
import com.chris64233.baggagerouting.api.EventView;
import com.chris64233.baggagerouting.api.ExceptionCaseView;
import com.chris64233.baggagerouting.api.ExceptionChainResponse;
import com.chris64233.baggagerouting.api.GenerationView;
import com.chris64233.baggagerouting.api.RoutePlanResponse;
import com.chris64233.baggagerouting.api.SegmentView;
import com.chris64233.baggagerouting.domain.Baggage;
import com.chris64233.baggagerouting.domain.BaggageEvent;
import com.chris64233.baggagerouting.domain.ExceptionCase;
import com.chris64233.baggagerouting.domain.RouteGeneration;
import com.chris64233.baggagerouting.domain.RouteSegment;
import com.chris64233.baggagerouting.domain.SegmentStatus;
import java.util.List;
import org.springframework.stereotype.Component;

/** 领域对象 → 只读查询视图的组装器。 */
@Component
public class BaggageViewMapper {

    public BaggageStateView state(Baggage baggage) {
        RouteGeneration active = baggage.getActiveGeneration();
        return switch (baggage.getStatus()) {
            case AT_LOCATION -> new BaggageStateView(
                    baggage.getStatus().name(), baggage.getCurrentLocation(), null, null,
                    active == null ? null : active.getGenerationNo());
            case ON_SEGMENT -> {
                RouteSegment seg = baggage.getCurrentSegment();
                yield new BaggageStateView(
                        baggage.getStatus().name(), null, seg.getId(), seg.getFlightNumber(),
                        active == null ? null : active.getGenerationNo());
            }
        };
    }

    public BaggageStatusResponse status(Baggage baggage) {
        return new BaggageStatusResponse(
                baggage.getTag(),
                baggage.getPassengerItinerary(),
                baggage.getPassengerName(),
                baggage.getFinalDestination(),
                state(baggage),
                baggage.getEvents().size());
    }

    public RoutePlanResponse routePlan(Baggage baggage, List<RouteGeneration> generations) {
        Long activeId = baggage.getActiveGeneration() == null ? null : baggage.getActiveGeneration().getId();
        List<GenerationView> views = generations.stream()
                .map(g -> generationView(g, g.getId().equals(activeId)))
                .toList();
        return new RoutePlanResponse(
                baggage.getTag(),
                baggage.getActiveGeneration() == null ? null
                        : baggage.getActiveGeneration().getGenerationNo(),
                views);
    }

    public GenerationView generationView(RouteGeneration generation, boolean active) {
        List<SegmentView> segments = generation.getSegments().stream()
                .sorted((a, b) -> Integer.compare(a.getSegmentNo(), b.getSegmentNo()))
                .map(this::segmentView)
                .toList();
        return new GenerationView(
                generation.getGenerationNo(),
                generation.getStatus().name(),
                generation.getOrigin(),
                generation.getDestination(),
                generation.getRebookOrderNo(),
                generation.getSourceExceptionNo(),
                active,
                segments);
    }

    public SegmentView segmentView(RouteSegment segment) {
        return new SegmentView(
                segment.getSegmentNo(),
                segment.getTransportType(),
                segment.getCarrier(),
                segment.getFlightNumber(),
                segment.getOrigin(),
                segment.getDestination(),
                segment.getStatus().name(),
                statusReason(segment.getStatus()));
    }

    private String statusReason(SegmentStatus status) {
        return switch (status) {
            case SUPERSEDED -> "改签取消";
            case CANCELLED -> "误装后取消";
            case ABORTED -> "误装中止";
            default -> null;
        };
    }

    public EventView eventView(BaggageEvent event) {
        RouteSegment seg = event.getSegment();
        return new EventView(
                event.getEventSeq(),
                event.getType().name(),
                event.getOccurredAt(),
                event.getExternalEventId(),
                event.getLocation(),
                seg == null ? null : seg.getId(),
                seg == null ? null : seg.getGeneration().getGenerationNo(),
                seg == null ? null : seg.getSegmentNo(),
                event.getFlightNumber(),
                event.getFromParty(),
                event.getToParty(),
                event.getExceptionNo(),
                event.getNote());
    }

    public ExceptionChainResponse exceptionChain(Baggage baggage, List<ExceptionCase> cases) {
        List<ExceptionCaseView> views = cases.stream().map(this::exceptionView).toList();
        return new ExceptionChainResponse(baggage.getTag(), cases.size(), views);
    }

    public ExceptionCaseView exceptionView(ExceptionCase exceptionCase) {
        RouteGeneration continuation = exceptionCase.getContinuationGeneration();
        List<SegmentView> continuationSegments = continuation == null ? List.of()
                : continuation.getSegments().stream()
                        .sorted((a, b) -> Integer.compare(a.getSegmentNo(), b.getSegmentNo()))
                        .map(this::segmentView)
                        .toList();
        RouteSegment aborted = exceptionCase.getAbortedSegment();
        return new ExceptionCaseView(
                exceptionCase.getCaseNo(),
                exceptionCase.getPreviousCaseNo(),
                exceptionCase.getExternalEventId(),
                exceptionCase.getRegisteredAt(),
                aborted.getFlightNumber(),
                aborted.getGeneration().getGenerationNo(),
                aborted.getSegmentNo(),
                exceptionCase.getActualFlightNumber(),
                exceptionCase.getFoundAtLocation(),
                exceptionCase.getReason(),
                continuation == null ? null : continuation.getGenerationNo(),
                continuationSegments);
    }
}
