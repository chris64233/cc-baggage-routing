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
 * 外部扫描事件幂等记录。externalEventId 全局唯一：
 * 重复扫描返回首次处理结果；若同一事件号携带的内容发生变化则报冲突（409）。
 */
@Entity
@Table(name = "external_event_record")
public class ExternalEventRecord {

    /** 外部事件号（主键由调用方提供，天然幂等键）。 */
    @Id
    @Column(name = "external_event_id", updatable = false, nullable = false, length = 64)
    private String externalEventId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "baggage_id", nullable = false, updatable = false)
    private Baggage baggage;

    /** 首次请求内容的哈希（SHA-256），用于检测内容变化。 */
    @Column(name = "payload_hash", nullable = false, updatable = false, length = 64)
    private String payloadHash;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    /** 首次处理结果的 JSON，重放时原样返回。 */
    @Lob
    @Column(name = "result_json", nullable = false, updatable = false)
    private String resultJson;

    protected ExternalEventRecord() {
    }

    public ExternalEventRecord(String externalEventId, Baggage baggage, String payloadHash,
                               Instant processedAt, String resultJson) {
        this.externalEventId = externalEventId;
        this.baggage = baggage;
        this.payloadHash = payloadHash;
        this.processedAt = processedAt;
        this.resultJson = resultJson;
    }

    public String getExternalEventId() {
        return externalEventId;
    }

    public Baggage getBaggage() {
        return baggage;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public String getResultJson() {
        return resultJson;
    }
}
