package de.dtfb.sportshub.backend.matchset;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MatchSetRepository extends JpaRepository<MatchSet, Long> {
    Optional<MatchSet> findByUuid(UUID uuid);
}
