package de.dtfb.sportshub.backend.matchday;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MatchdayRepository extends JpaRepository<Matchday, Long> {
    Optional<Matchday> findByUuid(UUID uuid);
}
