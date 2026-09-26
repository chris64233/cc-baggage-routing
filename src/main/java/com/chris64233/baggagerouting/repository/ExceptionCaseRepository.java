package com.chris64233.baggagerouting.repository;

import com.chris64233.baggagerouting.domain.ExceptionCase;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExceptionCaseRepository extends JpaRepository<ExceptionCase, String> {

    List<ExceptionCase> findByBaggageIdOrderByRegisteredAtAsc(Long baggageId);

    Optional<ExceptionCase> findFirstByBaggageIdOrderByRegisteredAtDesc(Long baggageId);
}
