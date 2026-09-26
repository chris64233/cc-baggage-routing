package com.chris64233.baggagerouting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 不可变行李事件：装载、卸载、交接与异常卸载。
 *
 * <p>事件只追加（append-only）：所有业务列 updatable=false，实体不提供任何修改方法。
 * 已完成航段与历史交接记录因此永远无法被改签或异常处理改写。
 */
@Entity
@Table(name = "baggage_event")
public class BaggageEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "baggage_id", nullable = false, updatable = false)
    private Baggage baggage;

    /** 行李内单调递增的事件序号。 */
    @Column(name = "event_seq", nullable = false, updatable = false)
    private int eventSeq;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 24)
    private EventType type;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    /** 触发该事件的外部扫描事件号（可能为空，表示系统内部记录）。 */
    @Column(name = "external_event_id", updatable = false)
    private String externalEventId;

    /** 事件发生地点（卸载/交接时）。 */
    @Column(name = "location", updatable = false)
    private String location;

    /** 关联航段（装载/卸载/异常卸载时）。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "segment_id", updatable = false)
    private RouteSegment segment;

    @Column(name = "flight_number", updatable = false)
    private String flightNumber;

    /** 交出方（交接事件）。 */
    @Column(name = "from_party", updatable = false)
    private String fromParty;

    /** 接收方（交接事件）。 */
    @Column(name = "to_party", updatable = false)
    private String toParty;

    /** 异常处理编号（异常卸载事件）。 */
    @Column(name = "exception_no", updatable = false)
    private String exceptionNo;

    /** 备注（如误装说明）。 */
    @Column(name = "note", length = 1000, updatable = false)
    private String note;

    protected BaggageEvent() {
    }

    private BaggageEvent(Baggage baggage, int eventSeq, EventType type, Instant occurredAt,
                         String externalEventId, String location, RouteSegment segment,
                         String flightNumber, String fromParty, String toParty,
                         String exceptionNo, String note) {
        this.baggage = baggage;
        this.eventSeq = eventSeq;
        this.type = type;
        this.occurredAt = occurredAt;
        this.externalEventId = externalEventId;
        this.location = location;
        this.segment = segment;
        this.flightNumber = flightNumber;
        this.fromParty = fromParty;
        this.toParty = toParty;
        this.exceptionNo = exceptionNo;
        this.note = note;
    }

    public static BaggageEvent load(Baggage baggage, int eventSeq, Instant occurredAt,
                                    String externalEventId, RouteSegment segment) {
        return new BaggageEvent(baggage, eventSeq, EventType.LOAD, occurredAt, externalEventId,
                segment.getOrigin(), segment, segment.getFlightNumber(), null, null, null, null);
    }

    public static BaggageEvent unload(Baggage baggage, int eventSeq, Instant occurredAt,
                                      String externalEventId, RouteSegment segment, String location) {
        return new BaggageEvent(baggage, eventSeq, EventType.UNLOAD, occurredAt, externalEventId,
                location, segment, segment.getFlightNumber(), null, null, null, null);
    }

    public static BaggageEvent handover(Baggage baggage, int eventSeq, Instant occurredAt,
                                        String externalEventId, String location,
                                        String fromParty, String toParty) {
        return new BaggageEvent(baggage, eventSeq, EventType.HANDOVER, occurredAt, externalEventId,
                location, null, null, fromParty, toParty, null, null);
    }

    public static BaggageEvent exceptionUnload(Baggage baggage, int eventSeq, Instant occurredAt,
                                               String externalEventId, RouteSegment segment,
                                               String location, String exceptionNo, String note) {
        return new BaggageEvent(baggage, eventSeq, EventType.EXCEPTION_UNLOAD, occurredAt,
                externalEventId, location, segment, segment.getFlightNumber(),
                null, null, exceptionNo, note);
    }

    public Long getId() {
        return id;
    }

    public Baggage getBaggage() {
        return baggage;
    }

    public int getEventSeq() {
        return eventSeq;
    }

    public EventType getType() {
        return type;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getExternalEventId() {
        return externalEventId;
    }

    public String getLocation() {
        return location;
    }

    public RouteSegment getSegment() {
        return segment;
    }

    public String getFlightNumber() {
        return flightNumber;
    }

    public String getFromParty() {
        return fromParty;
    }

    public String getToParty() {
        return toParty;
    }

    public String getExceptionNo() {
        return exceptionNo;
    }

    public String getNote() {
        return note;
    }
}
