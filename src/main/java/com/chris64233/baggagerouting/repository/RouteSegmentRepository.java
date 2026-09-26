package com.chris64233.baggagerouting.repository;

import java.util.List;

import com.chris64233.baggagerouting.domain.RouteSegment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RouteSegmentRepository extends JpaRepository<RouteSegment, Long> {

    @Query("select s from RouteSegment s where s.route.baggage.id = :baggageId")
    List<RouteSegment> findByBaggageId(@Param("baggageId") Long baggageId);
}
