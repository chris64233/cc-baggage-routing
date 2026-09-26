package com.chris64233.baggagerouting.repository;

import com.chris64233.baggagerouting.domain.ExternalEventRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalEventRecordRepository extends JpaRepository<ExternalEventRecord, String> {
}
