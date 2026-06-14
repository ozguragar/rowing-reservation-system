package com.rowingclub.backend.repository;

import com.rowingclub.backend.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findAllByOrderByTimestampDesc();
    List<AuditLog> findByActionContainingIgnoreCaseOrderByTimestampDesc(String action);
}
