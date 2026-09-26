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

/**
 * 行李异常处理记录。误装时先登记异常卸载（同时写入不可变 EXCEPTION_UNLOAD 事件），
 * 再挂接新的接续路线 recoveryRoute；禁止直接覆盖行李当前位置。
 * <p>
 * 异常按发生顺序形成处理链（通过前置异常 id 关联，查询时按 id 升序展开）。
 */
@Entity
@Table(name = "baggage_exception")
public class BaggageException {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "baggage_id", nullable = false)
    private Baggage baggage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExceptionType type;

    /** 误装时实际所在的航段。 */
    @Column(name = "wrong_segment_id", nullable = false)
    private Long wrongSegmentId;

    /** 异常卸载地点（行李被发现并卸下的地点）。 */
    @Column(name = "found_location_code", nullable = false)
    private String foundLocationCode;

    /** 异常卸载事件。 */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "exception_event_id", nullable = false)
    private BaggageEvent exceptionEvent;

    /** 新的接续计划路线。 */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "recovery_route_id", nullable = false)
    private Route recoveryRoute;

    /** 链上的前一个异常（首个异常为 null）。 */
    @Column(name = "previous_exception_id")
    private Long previousExceptionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExceptionStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected BaggageException() {
    }

    public BaggageException(Baggage baggage, ExceptionType type, Long wrongSegmentId, String foundLocationCode,
                            BaggageEvent exceptionEvent, Route recoveryRoute, Long previousExceptionId,
                            ExceptionStatus status, Instant createdAt) {
        this.baggage = baggage;
        this.type = type;
        this.wrongSegmentId = wrongSegmentId;
        this.foundLocationCode = foundLocationCode;
        this.exceptionEvent = exceptionEvent;
        this.recoveryRoute = recoveryRoute;
        this.previousExceptionId = previousExceptionId;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Baggage getBaggage() {
        return baggage;
    }

    public ExceptionType getType() {
        return type;
    }

    public Long getWrongSegmentId() {
        return wrongSegmentId;
    }

    public String getFoundLocationCode() {
        return foundLocationCode;
    }

    public BaggageEvent getExceptionEvent() {
        return exceptionEvent;
    }

    public Route getRecoveryRoute() {
        return recoveryRoute;
    }

    public Long getPreviousExceptionId() {
        return previousExceptionId;
    }

    public ExceptionStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
