package de.dtfb.sportshub.backend.matchday;

import de.dtfb.sportshub.backend.round.Round;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MatchDayRepository extends JpaRepository<MatchDay, String> {
    Optional<MatchDay> findByRoundAndName(Round round, String name);

    @Query("select e from MatchDay e where e.round.group.tier.league.season.archivedAt is null")
    List<MatchDay> findAllVisible();

    @Query("select e from MatchDay e where e.id = :id and e.round.group.tier.league.season.archivedAt is null")
    Optional<MatchDay> findVisibleById(String id);

    /** Whether the team has any recorded match day in this league (home or away). */
    @Query("select count(m) > 0 from MatchDay m where m.round.group.tier.league.id = :leagueId "
        + "and (m.teamHome.id = :teamId or m.teamAway.id = :teamId)")
    boolean existsByLeagueIdAndTeamId(@Param("leagueId") String leagueId, @Param("teamId") String teamId);
}
