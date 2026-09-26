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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.ArrayList;
import java.util.List;

/**
 * 行李聚合根：唯一标签 + 旅客行程 + 顺序航段（通过路线世代挂接）。
 *
 * <p>当前位置二选一不变量：{@link #status} 为 AT_LOCATION 时 currentLocation 有值；
 * 为 ON_SEGMENT 时 currentSegment 有值。所有迁移只能通过领域方法完成，
 * 误装处理也不允许直接覆盖当前位置，必须登记异常卸载事件。
 */
@Entity
@Table(name = "baggage")
public class Baggage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 行李唯一标签号。 */
    @Column(nullable = false, unique = true, updatable = false)
    private String tag;

    /** 旅客行程标识（如旅客订座/PNR）。 */
    @Column(nullable = false, updatable = false)
    private String passengerItinerary;

    /** 旅客姓名。 */
    @Column(nullable = false, updatable = false)
    private String passengerName;

    /** 行程最终目的地（也是最后一个航段的到达点）。 */
    @Column(nullable = false, updatable = false)
    private String finalDestination;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BaggageStatus status;

    /** 当前所在地点（status=AT_LOCATION 时有值）。 */
    @Column(name = "current_location")
    private String currentLocation;

    /** 当前所在航段（status=ON_SEGMENT 时有值）。 */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_segment_id")
    private RouteSegment currentSegment;

    /** 当前生效的路线世代。 */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "active_generation_id")
    private RouteGeneration activeGeneration;

    @OneToMany(mappedBy = "baggage", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BaggageEvent> events = new ArrayList<>();

    @Version
    private long version;

    protected Baggage() {
    }

    public Baggage(String tag, String passengerItinerary, String passengerName,
                   String currentLocation, String finalDestination) {
        this.tag = tag;
        this.passengerItinerary = passengerItinerary;
        this.passengerName = passengerName;
        this.currentLocation = currentLocation;
        this.finalDestination = finalDestination;
        this.status = BaggageStatus.AT_LOCATION;
    }

    /** 从地点装载到航段上（地点 → 航段）。 */
    public void moveOnto(RouteSegment segment) {
        if (status != BaggageStatus.AT_LOCATION) {
            throw new IllegalStateException("行李必须处于地点状态才能装载");
        }
        this.currentLocation = null;
        this.currentSegment = segment;
        this.status = BaggageStatus.ON_SEGMENT;
    }

    /** 从航段卸载到地点（航段 → 地点）。 */
    public void moveToLocation(String location) {
        if (status != BaggageStatus.ON_SEGMENT) {
            throw new IllegalStateException("行李必须处于航段状态才能卸载");
        }
        this.currentSegment = null;
        this.currentLocation = location;
        this.status = BaggageStatus.AT_LOCATION;
    }

    /** 交接：责任方变更不改变“在地点 / 在航段”的物理状态。 */
    public void attachEvent(BaggageEvent event) {
        this.events.add(event);
    }

    public void setActiveGeneration(RouteGeneration generation) {
        this.activeGeneration = generation;
    }

    public Long getId() {
        return id;
    }

    public String getTag() {
        return tag;
    }

    public String getPassengerItinerary() {
        return passengerItinerary;
    }

    public String getPassengerName() {
        return passengerName;
    }

    public String getFinalDestination() {
        return finalDestination;
    }

    public BaggageStatus getStatus() {
        return status;
    }

    public String getCurrentLocation() {
        return currentLocation;
    }

    public RouteSegment getCurrentSegment() {
        return currentSegment;
    }

    public RouteGeneration getActiveGeneration() {
        return activeGeneration;
    }

    public List<BaggageEvent> getEvents() {
        return events;
    }

    public long getVersion() {
        return version;
    }
}
