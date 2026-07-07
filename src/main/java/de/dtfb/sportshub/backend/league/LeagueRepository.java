package de.dtfb.sportshub.backend.league;

import de.dtfb.sportshub.backend.season.Season;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LeagueRepository extends JpaRepository<League, String> {
    Optional<League> findBySeasonAndName(Season season, String name);

    /** Leagues of active (non-archived) seasons -- hides an archived season's subtree. */
    List<League> findBySeason_ArchivedAtIsNull();

    /** Leagues of one season (used by copy-forward to walk the source subtree). */
    List<League> findBySeasonId(String seasonId);
}
