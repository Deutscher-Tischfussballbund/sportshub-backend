package de.dtfb.sportshub.backend.competition;

import de.dtfb.sportshub.backend.season.Season;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompetitionRepository extends JpaRepository<Competition, String> {
    Optional<Competition> findBySeasonAndName(Season season, String name);
}
