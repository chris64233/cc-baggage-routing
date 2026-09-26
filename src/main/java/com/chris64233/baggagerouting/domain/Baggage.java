package com.chris64233.baggagerouting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

/**
 * 行李记录：唯一行李标签、所属旅客行程。
 * <p>
 * 单一位置不变式：currentLocationCode 与 currentSegmentId 必须且只能有一个非空，
 * 即行李在任一时刻要么处于一个明确地点，要么处于一个运输航段上。
 */
@Entity
@Table(name = "baggage", uniqueConstraints = @UniqueConstraint(columnNames = "tag"))
public class Baggage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String tag;

    @Column(nullable = false)
    private String passengerName;

    @Column(nullable = false)
    private String itineraryRef;

    /** 行李当前所在地点；在运输航段上时为 null。 */
    @Column(name = "current_location_code")
    private String currentLocationCode;

    /** 行李当前所在航段；处于明确地点时为 null。 */
    @Column(name = "current_segment_id")
    private Long currentSegmentId;

    @Version
    private long version;

    protected Baggage() {
    }

    public Baggage(String tag, String passengerName, String itineraryRef, String currentLocationCode) {
        this.tag = tag;
        this.passengerName = passengerName;
        this.itineraryRef = itineraryRef;
        this.currentLocationCode = currentLocationCode;
    }

    public boolean isOnSegment() {
        return currentSegmentId != null;
    }

    /** 移动到一个明确地点。 */
    public void moveToLocation(String locationCode) {
        this.currentLocationCode = locationCode;
        this.currentSegmentId = null;
    }

    /** 装载到运输航段上。 */
    public void moveToSegment(Long segmentId) {
        this.currentSegmentId = segmentId;
        this.currentLocationCode = null;
    }

    public Long getId() {
        return id;
    }

    public String getTag() {
        return tag;
    }

    public String getPassengerName() {
        return passengerName;
    }

    public String getItineraryRef() {
        return itineraryRef;
    }

    public String getCurrentLocationCode() {
        return currentLocationCode;
    }

    public Long getCurrentSegmentId() {
        return currentSegmentId;
    }

    public long getVersion() {
        return version;
    }
}
