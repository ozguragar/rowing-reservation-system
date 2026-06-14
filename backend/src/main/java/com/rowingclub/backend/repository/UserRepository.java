package com.rowingclub.backend.repository;

import com.rowingclub.backend.entity.User;
import com.rowingclub.backend.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findByRole(Role role);
    List<User> findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(String name, String email);

    List<User> findByClubId(Long clubId);

    @Query("SELECT u FROM User u WHERE u.club.id = :clubId AND (LOWER(u.fullName) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<User> searchByClubId(@Param("clubId") Long clubId, @Param("query") String query);
}
