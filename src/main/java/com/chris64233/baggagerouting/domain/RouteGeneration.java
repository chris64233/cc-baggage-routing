package com.chris64233.baggagerouting.domain;

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
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

/**
 * 路线世代。初始路线为 generationNo=1；每次确认改签或误装接续都会新建世代。
 * 旧世代及其已完成航段永久保留，保证实际事件与交接记录不被改写。
 */
@Entity
@Table(name = "route_generation")
public class RouteGeneration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "baggage_id", nullable = false, updatable = false)
    private Baggage baggage;

    @Column(name = "generation_no", nullable = false, updatable = false)
    private int generationNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GenerationStatus status;

    /** 起点（首航段出发地）。 */
    @Column(name = "origin", nullable = false, updatable = false)
    private String origin;

    /** 终点（末航段到达地）。 */
    @Column(name = "destination", nullable = false, updatable = false)
    private String destination;

    /** 触发新世代的改签业务号（改签世代有值）。 */
    @Column(name = "rebook_order_no", updatable = false)
    private String rebookOrderNo;

    /** 触发新世代的异常处理编号（误装接续世代有值）。 */
    @Column(name = "source_exception_no", updatable = false)
    private String sourceExceptionNo;

    @OneToMany(mappedBy = "generation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RouteSegment> segments = new ArrayList<>();

    protected RouteGeneration() {
    }

    private RouteGeneration(Baggage baggage, int generationNo, String origin, String destination,
                            String rebookOrderNo, String sourceExceptionNo) {
        this.baggage = baggage;
        this.generationNo = generationNo;
        this.origin = origin;
        this.destination = destination;
        this.rebookOrderNo = rebookOrderNo;
        this.sourceExceptionNo = sourceExceptionNo;
        this.status = GenerationStatus.ACTIVE;
    }

    /** 初始路线。 */
    public static RouteGeneration initial(Baggage baggage, int generationNo,
                                          String origin, String destination) {
        return new RouteGeneration(baggage, generationNo, origin, destination, null, null);
    }

    /** 改签产生的替代路线。 */
    public static RouteGeneration rebooked(Baggage baggage, int generationNo,
                                           String origin, String destination, String rebookOrderNo) {
        return new RouteGeneration(baggage, generationNo, origin, destination, rebookOrderNo, null);
    }

    /** 误装后产生的接续路线。 */
    public static RouteGeneration continuation(Baggage baggage, int generationNo,
                                               String origin, String destination, String sourceExceptionNo) {
        return new RouteGeneration(baggage, generationNo, origin, destination, null, sourceExceptionNo);
    }

    public void markSuperseded() {
        this.status = GenerationStatus.SUPERSEDED;
    }

    public void markAborted() {
        this.status = GenerationStatus.ABORTED;
    }

    public Long getId() {
        return id;
    }

    public Baggage getBaggage() {
        return baggage;
    }

    public int getGenerationNo() {
        return generationNo;
    }

    public GenerationStatus getStatus() {
        return status;
    }

    public String getOrigin() {
        return origin;
    }

    public String getDestination() {
        return destination;
    }

    public String getRebookOrderNo() {
        return rebookOrderNo;
    }

    public String getSourceExceptionNo() {
        return sourceExceptionNo;
    }

    public List<RouteSegment> getSegments() {
        return segments;
    }
}
