package com.chris64233.baggagerouting.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 误装异常处理记录：登记异常卸载 + 新接续计划。
 *
 * <p>误装不允许直接覆盖行李当前位置：必须先追加 EXCEPTION_UNLOAD 事件把行李
 * 合法地从误装航段移动到发现地点，再生成接续世代。异常记录通过 previousCaseNo
 * 串成异常处理链，便于完整追溯每次误装及其处置。
 */
@Entity
@Table(name = "exception_case")
public class ExceptionCase {

    /** 异常处理编号（幂等键，由服务端按标签分配）。 */
    @Id
    @Column(name = "case_no", updatable = false, nullable = false, length = 64)
    private String caseNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "baggage_id", nullable = false, updatable = false)
    private Baggage baggage;

    /** 误装时行李实际所在航段。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aborted_segment_id", nullable = false, updatable = false)
    private RouteSegment abortedSegment;

    /** 异常卸载的发现地点（接续路线起点）。 */
    @Column(name = "found_at_location", nullable = false, updatable = false)
    private String foundAtLocation;

    /** 误装时实际扫描到的航班号。 */
    @Column(name = "actual_flight_number", nullable = false, updatable = false)
    private String actualFlightNumber;

    @Column(name = "reason", nullable = false, updatable = false, length = 1000)
    private String reason;

    /** 本次异常处理生成的接续世代。 */
    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "continuation_generation_id", updatable = false)
    private RouteGeneration continuationGeneration;

    /** 前一次异常处理编号（同一行李多次误装时形成链条）。 */
    @Column(name = "previous_case_no", updatable = false, length = 64)
    private String previousCaseNo;

    /** 触发该异常处理的外部事件号。 */
    @Column(name = "external_event_id", updatable = false, length = 64)
    private String externalEventId;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private Instant registeredAt;

    protected ExceptionCase() {
    }

    public ExceptionCase(String caseNo, Baggage baggage, RouteSegment abortedSegment,
                         String foundAtLocation, String actualFlightNumber, String reason,
                         RouteGeneration continuationGeneration, String previousCaseNo,
                         String externalEventId, Instant registeredAt) {
        this.caseNo = caseNo;
        this.baggage = baggage;
        this.abortedSegment = abortedSegment;
        this.foundAtLocation = foundAtLocation;
        this.actualFlightNumber = actualFlightNumber;
        this.reason = reason;
        this.continuationGeneration = continuationGeneration;
        this.previousCaseNo = previousCaseNo;
        this.externalEventId = externalEventId;
        this.registeredAt = registeredAt;
    }

    public String getCaseNo() {
        return caseNo;
    }

    public Baggage getBaggage() {
        return baggage;
    }

    public RouteSegment getAbortedSegment() {
        return abortedSegment;
    }

    public String getFoundAtLocation() {
        return foundAtLocation;
    }

    public String getActualFlightNumber() {
        return actualFlightNumber;
    }

    public String getReason() {
        return reason;
    }

    public RouteGeneration getContinuationGeneration() {
        return continuationGeneration;
    }

    public String getPreviousCaseNo() {
        return previousCaseNo;
    }

    public String getExternalEventId() {
        return externalEventId;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }
}
