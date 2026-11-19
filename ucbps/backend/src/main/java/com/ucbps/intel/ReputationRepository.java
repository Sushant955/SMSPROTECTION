package com.ucbps.intel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ReputationRepository extends JpaRepository<NumberReputation, String> {
    List<NumberReputation> findByFlaggedTrueAndUpdatedAtAfter(Instant since);

    long countByFlaggedTrue();
}
