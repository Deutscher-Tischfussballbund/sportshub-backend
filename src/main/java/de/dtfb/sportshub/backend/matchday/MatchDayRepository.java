package de.dtfb.sportshub.backend.matchday;

import de.dtfb.sportshub.backend.round.Round;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MatchDayRepository extends JpaRepository<MatchDay, String> {
    Optional<MatchDay> findByRoundAndName(Round round, String name);

    List<MatchDay> findByRoundGroupId(String groupId);

    /**
     * Whether any fixture of the group (other than a bye fixture, final from the start) has left
     * {@code state} -- i.e. has a result entered (game plan lock).
     */
    boolean existsByRound_Group_IdAndResultStateNotAndByeFalse(String groupId, ResultState state);

    /** Fixtures in a group, in {@code state}, without any games yet (game backfill, SPO-71). */
    @Query("select d from MatchDay d where d.round.group is not null and d.resultState = :state "
        + "and not exists (select m from Match m where m.matchDay = d)")
    List<MatchDay> findWithoutGames(@Param("state") ResultState state);

    long countByLocationId(String locationId);

    /**
     * Entered, not yet final results in seasons that aren't archived (pending-results overview). Imported
     * fixtures (docs/29) are history -- an unconfirmed result from the old system is nobody's to-do.
     */
    @Query("""
        select e from MatchDay e where e.resultState = :state and e.round.group.tier.league.season.archivedAt is null
          and not exists (select 1 from ExternalReference r
            where r.entityType = de.dtfb.sportshub.backend.importer.ImportRecordType.FIXTURE and r.entityId = e.id)""")
    List<MatchDay> findVisibleByResultState(@Param("state") ResultState state);

    @Query("select e from MatchDay e where e.round.group.tier.league.season.archivedAt is null")
    List<MatchDay> findAllVisible();

    @Query("select e from MatchDay e where e.id = :id and e.round.group.tier.league.season.archivedAt is null")
    Optional<MatchDay> findVisibleById(String id);

    /** Whether the team has any recorded match day in this league (home or away). */
    @Query("select count(m) > 0 from MatchDay m where m.round.group.tier.league.id = :leagueId "
        + "and (m.teamHome.id = :teamId or m.teamAway.id = :teamId)")
    boolean existsByLeagueIdAndTeamId(@Param("leagueId") String leagueId, @Param("teamId") String teamId);
}
