package com.rowingclub.backend.repository;

import com.rowingclub.backend.entity.Club;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClubRepository extends JpaRepository<Club, Long> {
    Optional<Club> findByName(String name);

    /** Lowest-id club — used as the default target for public self-registration. */
    Optional<Club> findFirstByOrderByIdAsc();
}
