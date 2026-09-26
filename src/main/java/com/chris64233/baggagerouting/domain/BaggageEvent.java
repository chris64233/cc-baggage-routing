package com.chris64233.baggagerouting.domain;

import java.time.Instant;

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
import jakarta.persistence.UniqueConstraint;

/**
 * 行李的装载、卸载、交接事件，一经写入不可修改、不可删除。
 * <p>
 * externalEventRef 为外部扫描事件号，全局唯一：重复扫描同一外部事件返回首次结果；
 * payloadHash 为事件内容指纹，事件内容变化时判定为冲突。
 */
@Entity
@Table(name = "baggage_event",
        uniqueConstraints = @UniqueConstraint(columnNames = "external_event_ref"))
public class BaggageEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "baggage_id", nullable = false)
    private Baggage baggage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventType type;

    /** 航班/运输工具号；交接事件可为空。 */
    @Column(name = "flight_no")
    private String flightNo;

    /** 事件发生地点（装载起点、卸载终点、交接点）。 */
    @Column(nullable = false)
    private String locationCode;

    @Column(name = "external_event_ref", nullable = false)
    private String externalEventRef;

    /** 外部事件内容指纹，用于检测同一事件号内容被篡改。 */
    @Column(name = "payload_hash", nullable = false)
    private String payloadHash;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    protected BaggageEvent() {
    }

    public BaggageEvent(Baggage baggage, EventType type, String flightNo, String locationCode,
                        String externalEventRef, String payloadHash, Instant occurredAt, Instant recordedAt) {
        this.baggage = baggage;
        this.type = type;
        this.flightNo = flightNo;
        this.locationCode = locationCode;
        this.externalEventRef = externalEventRef;
        this.payloadHash = payloadHash;
        this.occurredAt = occurredAt;
        this.recordedAt = recordedAt;
    }

    public Long getId() {
        return id;
    }

    public Baggage getBaggage() {
        return baggage;
    }

    public EventType getType() {
        return type;
    }

    public String getFlightNo() {
        return flightNo;
    }

    public String getLocationCode() {
        return locationCode;
    }

    public String getExternalEventRef() {
        return externalEventRef;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
