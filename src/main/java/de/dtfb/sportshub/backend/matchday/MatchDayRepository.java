package de.dtfb.sportshub.backend.matchday;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MatchDayRepository extends JpaRepository<MatchDay, Long> {
    Optional<MatchDay> findByUuid(UUID uuid);
}
