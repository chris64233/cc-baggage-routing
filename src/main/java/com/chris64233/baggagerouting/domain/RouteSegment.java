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
 * 路线上的单个运输航段（如一个航班），按 seq 顺序排列。
 */
@Entity
@Table(name = "route_segment")
public class RouteSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    @Column(nullable = false)
    private int seq;

    @Column(nullable = false)
    private String flightNo;

    @Column(nullable = false)
    private String origin;

    @Column(nullable = false)
    private String destination;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SegmentStatus status;

    protected RouteSegment() {
    }

    public RouteSegment(Route route, int seq, String flightNo, String origin, String destination,
                        SegmentStatus status) {
        this.route = route;
        this.seq = seq;
        this.flightNo = flightNo;
        this.origin = origin;
        this.destination = destination;
        this.status = status;
    }

    public void setStatus(SegmentStatus status) {
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public Route getRoute() {
        return route;
    }

    public int getSeq() {
        return seq;
    }

    public String getFlightNo() {
        return flightNo;
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
