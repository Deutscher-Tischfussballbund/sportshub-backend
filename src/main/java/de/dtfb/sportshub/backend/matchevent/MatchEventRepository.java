package de.dtfb.sportshub.backend.matchevent;

import de.dtfb.sportshub.backend.match.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MatchEventRepository extends JpaRepository<MatchEvent, String> {

    @Query("select e from MatchEvent e where e.match.matchDay.round.group.tier.league.season.archivedAt is null")
    List<MatchEvent> findAllVisible();

    /** The events of a fixture's games, e.g. its substitutions (docs/23). */
    List<MatchEvent> findByMatch_MatchDay_IdAndType(String matchDayId, MatchEventType type);

    /** All events of one game -- deleted with the game. */
    List<MatchEvent> findByMatch(Match match);

    @Query("select e from MatchEvent e where e.id = :id and e.match.matchDay.round.group.tier.league.season.archivedAt is null")
    Optional<MatchEvent> findVisibleById(String id);
}
