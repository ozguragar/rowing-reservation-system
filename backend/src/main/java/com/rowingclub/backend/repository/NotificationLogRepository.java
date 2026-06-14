package com.rowingclub.backend.repository;

import com.rowingclub.backend.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
    List<NotificationLog> findByUserIdOrderBySentAtDesc(Long userId);
}
