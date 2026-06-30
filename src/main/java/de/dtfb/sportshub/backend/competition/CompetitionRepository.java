package de.dtfb.sportshub.backend.competition;

import de.dtfb.sportshub.backend.season.Season;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompetitionRepository extends JpaRepository<Competition, String> {
    Optional<Competition> findBySeasonAndName(Season season, String name);

    /** Competitions of active (non-archived) seasons — hides an archived season's subtree. */
    List<Competition> findBySeason_ArchivedAtIsNull();
}
