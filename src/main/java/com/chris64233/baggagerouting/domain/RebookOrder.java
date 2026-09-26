package com.chris64233.baggagerouting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 改签单：业务号 orderNo 全局唯一，保证改签幂等。
 * 重复提交同一业务号返回首次结果，内容变化则冲突。
 */
@Entity
@Table(name = "rebook_order")
public class RebookOrder {

    /** 改签业务号（幂等键）。 */
    @Id
    @Column(name = "order_no", updatable = false, nullable = false, length = 64)
    private String orderNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "baggage_id", nullable = false, updatable = false)
    private Baggage baggage;

    @Column(name = "payload_hash", nullable = false, updatable = false, length = 64)
    private String payloadHash;

    @Column(name = "confirmed_at", nullable = false, updatable = false)
    private Instant confirmedAt;

    /** 首次改签结果 JSON（含新世代信息），重放原样返回。 */
    @Lob
    @Column(name = "result_json", nullable = false, updatable = false)
    private String resultJson;

    protected RebookOrder() {
    }

    public RebookOrder(String orderNo, Baggage baggage, String payloadHash,
                       Instant confirmedAt, String resultJson) {
        this.orderNo = orderNo;
        this.baggage = baggage;
        this.payloadHash = payloadHash;
        this.confirmedAt = confirmedAt;
        this.resultJson = resultJson;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public Baggage getBaggage() {
        return baggage;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public String getResultJson() {
        return resultJson;
    }
}
