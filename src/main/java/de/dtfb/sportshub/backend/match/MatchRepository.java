package de.dtfb.sportshub.backend.match;

import de.dtfb.sportshub.backend.matchday.MatchDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MatchRepository extends JpaRepository<Match, String> {
    Optional<Match> findByMatchDayAndEndTime(MatchDay matchDay, Instant endTime);
    List<Match> findByMatchDay(MatchDay matchDay);

    /** The games of many fixtures in one query (fixture scores in lists). */
    List<Match> findByMatchDayIn(Collection<MatchDay> matchDays);

    /** Whether any game in the group already has a score (game plan lock, SPO-71). */
    @Query("select count(m) > 0 from Match m where m.matchDay.round.group.id = :groupId "
        + "and (m.homeScore is not null or m.awayScore is not null)")
    boolean existsScoredInGroup(@Param("groupId") String groupId);

    @Query("select e from Match e where e.matchDay.round.group.tier.league.season.archivedAt is null")
    List<Match> findAllVisible();

    @Query("select e from Match e where e.id = :id and e.matchDay.round.group.tier.league.season.archivedAt is null")
    Optional<Match> findVisibleById(String id);
}
