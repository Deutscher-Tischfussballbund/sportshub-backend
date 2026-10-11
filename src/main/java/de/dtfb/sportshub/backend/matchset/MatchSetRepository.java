package de.dtfb.sportshub.backend.matchset;

import de.dtfb.sportshub.backend.match.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MatchSetRepository extends JpaRepository<MatchSet, String> {
    Optional<MatchSet> findByMatchAndSetNumber(Match match, Integer setNumber);

    List<MatchSet> findByMatch(Match match);

    /** Sets of one game or of every game of one fixture (SPO-120); a null filter doesn't narrow. */
    @Query("""
        select e from MatchSet e where e.match.matchDay.round.group.tier.league.season.archivedAt is null
          and (:matchId is null or e.match.id = :matchId)
          and (:matchDayId is null or e.match.matchDay.id = :matchDayId)
        order by e.match.position, e.setNumber""")
    List<MatchSet> findVisible(@Param("matchId") String matchId, @Param("matchDayId") String matchDayId);

    @Query("select e from MatchSet e where e.id = :id and e.match.matchDay.round.group.tier.league.season.archivedAt is null")
    Optional<MatchSet> findVisibleById(String id);
}
