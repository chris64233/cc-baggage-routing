package com.chris64233.baggagerouting.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 一条路线由按顺序排列的航段组成。同一行李存在多条路线时，
 * 只有一条 ACTIVE；改签/误装接续会将旧路线连同其未执行航段一起取消。
 * <p>
 * changeNo 为改签业务号：非空时全局唯一，用于改签确认的幂等。
 */
@Entity
@Table(name = "route", uniqueConstraints = @UniqueConstraint(columnNames = "change_no"))
public class Route {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "baggage_id", nullable = false)
    private Baggage baggage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RouteSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RouteStatus status;

    @Column(name = "change_no")
    private String changeNo;

    @Column(nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("seq ASC")
    private List<RouteSegment> segments = new ArrayList<>();

    protected Route() {
    }

    public Route(Baggage baggage, RouteSource source, RouteStatus status, String changeNo, Instant createdAt) {
        this.baggage = baggage;
        this.source = source;
        this.status = status;
        this.changeNo = changeNo;
        this.createdAt = createdAt;
    }

    public RouteSegment addSegment(int seq, String flightNo, String origin, String destination,
                                   SegmentStatus segmentStatus) {
        RouteSegment segment = new RouteSegment(this, seq, flightNo, origin, destination, segmentStatus);
        this.segments.add(segment);
        return segment;
    }

    /** 取消尚未执行（PLANNED）的航段；已装载或已完成航段不得改变。 */
    public void cancelUnloadedSegments() {
        for (RouteSegment segment : segments) {
            if (segment.getStatus() == SegmentStatus.PLANNED) {
                segment.setStatus(SegmentStatus.CANCELLED);
            }
        }
        this.status = RouteStatus.CANCELLED;
    }

    public Long getId() {
        return id;
    }

    public Baggage getBaggage() {
        return baggage;
    }

    public RouteSource getSource() {
        return source;
    }

    public RouteStatus getStatus() {
        return status;
    }

    public String getChangeNo() {
        return changeNo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<RouteSegment> getSegments() {
        return segments;
    }
}
