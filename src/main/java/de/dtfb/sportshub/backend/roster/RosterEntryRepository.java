package de.dtfb.sportshub.backend.roster;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RosterEntryRepository extends JpaRepository<RosterEntry, String> {

    /** Active roster of a participation (players currently on it). */
    List<RosterEntry> findByParticipationIdAndRemovedAtIsNull(String participationId);

    /** The active entry for a specific player, if present (dup guard + remove target). */
    Optional<RosterEntry> findByParticipationIdAndPlayerIdAndRemovedAtIsNull(String participationId, String playerId);

    /**
     * Whether the user plays (via a {@code Player} linked to their login) on the team's current roster
     * in the league -- "team member" for result entry (docs/17).
     */
    @Query("select count(r) > 0 from RosterEntry r where r.removedAt is null "
        + "and r.participation.team.id = :teamId and r.participation.league.id = :leagueId "
        + "and r.player.user.id = :userId")
    boolean isOnActiveRoster(@Param("userId") String userId, @Param("teamId") String teamId,
                             @Param("leagueId") String leagueId);
}
