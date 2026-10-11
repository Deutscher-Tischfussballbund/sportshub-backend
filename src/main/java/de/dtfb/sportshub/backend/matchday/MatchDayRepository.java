package de.dtfb.sportshub.backend.matchday;

import de.dtfb.sportshub.backend.round.Round;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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

    /**
     * Fixtures of seasons that aren't archived, narrowed by the optional filters (SPO-120); a null filter
     * doesn't narrow. {@code teamId} matches either side, by
     * team row or team identity (the team area addresses a team across seasons); {@code from}/{@code to} bound the kick-off
     * ({@code to} exclusive), fixtures without one drop out once either is set.
     */
    @Query("""
        select e from MatchDay e where e.round.group.tier.league.season.archivedAt is null
          and (:groupId is null or e.round.group.id = :groupId)
          and (:roundId is null or e.round.id = :roundId)
          and (:leagueId is null or e.round.group.tier.league.id = :leagueId)
          and (:seasonId is null or e.round.group.tier.league.season.id = :seasonId)
          and (:teamId is null or e.teamHome.id = :teamId or e.teamAway.id = :teamId
               or e.teamHome.teamIdentityId = :teamId or e.teamAway.teamIdentityId = :teamId)
          and (:from is null or e.startDate >= :from)
          and (:to is null or e.startDate < :to)
          and (:state is null or e.resultState = :state)""")
    List<MatchDay> findVisible(@Param("groupId") String groupId, @Param("roundId") String roundId,
                               @Param("leagueId") String leagueId, @Param("seasonId") String seasonId,
                               @Param("teamId") String teamId, @Param("from") Instant from,
                               @Param("to") Instant to, @Param("state") ResultState state);

    @Query("select e from MatchDay e where e.id = :id and e.round.group.tier.league.season.archivedAt is null")
    Optional<MatchDay> findVisibleById(String id);

    /** Whether the team has any recorded match day in this league (home or away). */
    @Query("select count(m) > 0 from MatchDay m where m.round.group.tier.league.id = :leagueId "
        + "and (m.teamHome.id = :teamId or m.teamAway.id = :teamId)")
    boolean existsByLeagueIdAndTeamId(@Param("leagueId") String leagueId, @Param("teamId") String teamId);
}
