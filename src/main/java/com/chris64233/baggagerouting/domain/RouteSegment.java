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

/**
 * 计划航段：同一世代内按 segmentNo 顺序排列，相邻航段首尾必须衔接。
 * 航段状态是装载/卸载/改签/误装一致性判断的核心依据。
 */
@Entity
@Table(name = "route_segment")
public class RouteSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "generation_id", nullable = false, updatable = false)
    private RouteGeneration generation;

    @Column(name = "segment_no", nullable = false, updatable = false)
    private int segmentNo;

    /** 航段类型（航班/卡车/驳运等）。 */
    @Column(name = "transport_type", nullable = false, updatable = false, length = 24)
    private String transportType;

    /** 承运人/外部运输工具号。 */
    @Column(name = "carrier", nullable = false, updatable = false)
    private String carrier;

    /** 航班号（或车次/车次号），装载扫描时必须与计划一致，防止误装。 */
    @Column(name = "flight_number", nullable = false, updatable = false)
    private String flightNumber;

    @Column(name = "origin", nullable = false, updatable = false)
    private String origin;

    @Column(name = "destination", nullable = false, updatable = false)
    private String destination;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SegmentStatus status;

    protected RouteSegment() {
    }

    public RouteSegment(RouteGeneration generation, int segmentNo, String transportType,
                        String carrier, String flightNumber, String origin, String destination) {
        this.generation = generation;
        this.segmentNo = segmentNo;
        this.transportType = transportType;
        this.carrier = carrier;
        this.flightNumber = flightNumber;
        this.origin = origin;
        this.destination = destination;
        this.status = SegmentStatus.PLANNED;
        generation.getSegments().add(this);
    }

    public void markInProgress() {
        if (status != SegmentStatus.PLANNED) {
            throw new IllegalStateException("只有计划航段可以开始执行");
        }
        this.status = SegmentStatus.IN_PROGRESS;
    }

    public void markCompleted() {
        if (status != SegmentStatus.IN_PROGRESS) {
            throw new IllegalStateException("只有执行中航段可以完成");
        }
        this.status = SegmentStatus.COMPLETED;
    }

    /** 改签：旧世代未执行航段作废。 */
    public void markSuperseded() {
        if (status != SegmentStatus.PLANNED) {
            throw new IllegalStateException("只有未执行航段可被改签取消");
        }
        this.status = SegmentStatus.SUPERSEDED;
    }

    /** 误装：实际停留航段提前终止。 */
    public void markAborted() {
        this.status = SegmentStatus.ABORTED;
    }

    /** 误装：剩余未执行航段不再可达。 */
    public void markCancelled() {
        if (status != SegmentStatus.PLANNED) {
            throw new IllegalStateException("只有未执行航段可被取消");
        }
        this.status = SegmentStatus.CANCELLED;
    }

    public Long getId() {
        return id;
    }

    public RouteGeneration getGeneration() {
        return generation;
    }

    public int getSegmentNo() {
        return segmentNo;
    }

    public String getTransportType() {
        return transportType;
    }

    public String getCarrier() {
        return carrier;
    }

    public String getFlightNumber() {
        return flightNumber;
    }

    public String getOrigin() {
        return origin;
    }

    public String getDestination() {
        return destination;
    }

    public SegmentStatus getStatus() {
        return status;
    }
}
