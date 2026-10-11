package de.dtfb.sportshub.backend.matchevent;

import de.dtfb.sportshub.backend.match.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MatchEventRepository extends JpaRepository<MatchEvent, String> {

    /** Events of one game or of every game of one fixture (SPO-120); a null filter doesn't narrow. */
    @Query("""
        select e from MatchEvent e where e.match.matchDay.round.group.tier.league.season.archivedAt is null
          and (:matchId is null or e.match.id = :matchId)
          and (:matchDayId is null or e.match.matchDay.id = :matchDayId)""")
    List<MatchEvent> findVisible(@Param("matchId") String matchId, @Param("matchDayId") String matchDayId);

    /** The events of a fixture's games, e.g. its substitutions (docs/23). */
    List<MatchEvent> findByMatch_MatchDay_IdAndType(String matchDayId, MatchEventType type);

    /** All events of one game -- deleted with the game. */
    List<MatchEvent> findByMatch(Match match);

    @Query("select e from MatchEvent e where e.id = :id and e.match.matchDay.round.group.tier.league.season.archivedAt is null")
    Optional<MatchEvent> findVisibleById(String id);
}
