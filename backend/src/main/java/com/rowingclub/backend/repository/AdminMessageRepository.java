package com.rowingclub.backend.repository;

import com.rowingclub.backend.entity.AdminMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AdminMessageRepository extends JpaRepository<AdminMessage, Long> {
    List<AdminMessage> findAllByOrderByCreatedAtDesc();
    List<AdminMessage> findByIsResolvedFalseOrderByCreatedAtDesc();
    List<AdminMessage> findByClubIdAndIsResolvedFalseOrderByCreatedAtDesc(Long clubId);
    List<AdminMessage> findByClubIdOrderByCreatedAtDesc(Long clubId);
}
