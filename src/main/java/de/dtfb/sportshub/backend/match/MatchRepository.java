package de.dtfb.sportshub.backend.match;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MatchRepository extends JpaRepository<Match, Long> {
    Optional<Match> findByUuid(UUID uuid);
}
