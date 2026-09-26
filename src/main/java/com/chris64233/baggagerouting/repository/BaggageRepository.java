package com.chris64233.baggagerouting.repository;

import com.chris64233.baggagerouting.domain.Baggage;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface BaggageRepository extends JpaRepository<Baggage, Long> {

    Optional<Baggage> findByTag(String tag);

    /**
     * 行级悲观锁读取行李。装载、卸载、交接、改签、误装登记都先取锁，
     * 将同一行李的状态迁移串行化，保证并发装载与改签只能形成一种一致结果。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select b from Baggage b where b.tag = :tag")
    Optional<Baggage> findByTagForUpdate(@Param("tag") String tag);

    boolean existsByTag(String tag);
}
