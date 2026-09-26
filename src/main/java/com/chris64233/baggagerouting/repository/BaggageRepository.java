package com.chris64233.baggagerouting.repository;

import java.util.Optional;

import com.chris64233.baggagerouting.domain.Baggage;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BaggageRepository extends JpaRepository<Baggage, Long> {

    Optional<Baggage> findByTag(String tag);

    /**
     * 写路径（装载/卸载/交接/改签/误装）统一走悲观锁取行李，
     * 保证并发装载与改签串行化，只能形成一种一致结果。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Baggage b where b.tag = :tag")
    Optional<Baggage> findByTagForUpdate(@Param("tag") String tag);
}
