package com.chris64233.baggagerouting.repository;

import java.util.List;
import java.util.Optional;

import com.chris64233.baggagerouting.domain.Route;
import com.chris64233.baggagerouting.domain.RouteStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteRepository extends JpaRepository<Route, Long> {

    List<Route> findByBaggageIdOrderByIdAsc(Long baggageId);

    Optional<Route> findByBaggageIdAndStatus(Long baggageId, RouteStatus status);

    Optional<Route> findByChangeNo(String changeNo);
}
