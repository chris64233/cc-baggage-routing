package com.chris64233.baggagerouting.repository;

import com.chris64233.baggagerouting.domain.RouteSegment;
import com.chris64233.baggagerouting.domain.SegmentStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteSegmentRepository extends JpaRepository<RouteSegment, Long> {

    List<RouteSegment> findByGenerationIdOrderBySegmentNoAsc(Long generationId);

    /** 当前生效世代的首个计划航段（下一个可装载航段）。 */
    Optional<RouteSegment> findFirstByGenerationIdAndStatusOrderBySegmentNoAsc(
            Long generationId, SegmentStatus status);

    long countByGenerationIdAndStatus(Long generationId, SegmentStatus status);
}
