package com.chris64233.baggagerouting.repository;

import com.chris64233.baggagerouting.domain.RouteGeneration;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RouteGenerationRepository extends JpaRepository<RouteGeneration, Long> {

    List<RouteGeneration> findByBaggageIdOrderByGenerationNoAsc(Long baggageId);

    /** 在持有行李行锁后读取当前最大世代号（锁内调用，无需额外加锁）。 */
    @Query("select coalesce(max(g.generationNo), 0) from RouteGeneration g where g.baggage.id = :baggageId")
    Integer findMaxGenerationNo(@Param("baggageId") Long baggageId);
}
