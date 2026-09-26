package com.chris64233.baggagerouting.repository;

import java.util.List;
import java.util.Optional;

import com.chris64233.baggagerouting.domain.BaggageEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BaggageEventRepository extends JpaRepository<BaggageEvent, Long> {

    List<BaggageEvent> findByBaggageIdOrderByIdAsc(Long baggageId);

    Optional<BaggageEvent> findByExternalEventRef(String externalEventRef);
}
