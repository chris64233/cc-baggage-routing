package com.chris64233.baggagerouting.repository;

import java.util.List;
import java.util.Optional;

import com.chris64233.baggagerouting.domain.BaggageException;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BaggageExceptionRepository extends JpaRepository<BaggageException, Long> {

    List<BaggageException> findByBaggageIdOrderByIdAsc(Long baggageId);

    Optional<BaggageException> findByExceptionEventId(Long exceptionEventId);
}
