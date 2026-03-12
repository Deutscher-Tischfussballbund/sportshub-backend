package de.dtfb.sportshub.backend.match;

import de.dtfb.sportshub.backend.matchday.MatchDay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface MatchRepository extends JpaRepository<Match, UUID> {
    Optional<Match> findByMatchDayAndEndTime(MatchDay matchDay, Instant endTime);
}
