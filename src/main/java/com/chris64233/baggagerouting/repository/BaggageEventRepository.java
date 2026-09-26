package com.chris64233.baggagerouting.repository;

import com.chris64233.baggagerouting.domain.BaggageEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BaggageEventRepository extends JpaRepository<BaggageEvent, Long> {

    List<BaggageEvent> findByBaggageIdOrderByEventSeqAsc(Long baggageId);
}
