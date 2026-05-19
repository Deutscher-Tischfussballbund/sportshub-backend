package de.dtfb.sportshub.backend.matchday;

import de.dtfb.sportshub.backend.round.Round;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MatchDayRepository extends JpaRepository<MatchDay, String> {
    Optional<MatchDay> findByRoundAndName(Round round, String name);
}
