package de.dtfb.sportshub.backend.teamparticipation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TeamParticipationRepository extends JpaRepository<TeamParticipation, String> {

    @Query("select p from TeamParticipation p where p.league.season.archivedAt is null")
    List<TeamParticipation> findAllVisible();

    @Query("select p from TeamParticipation p where p.id = :id and p.league.season.archivedAt is null")
    Optional<TeamParticipation> findVisibleById(String id);

    @Query("select p from TeamParticipation p where p.league.id = :leagueId and p.league.season.archivedAt is null")
    List<TeamParticipation> findVisibleByLeagueId(String leagueId);

    @Query("select p from TeamParticipation p where p.team.id = :teamId and p.league.season.archivedAt is null")
    List<TeamParticipation> findVisibleByTeamId(String teamId);

    @Query("select p from TeamParticipation p where p.league.season.federation.id = :federationId"
        + " and p.rosterStatus = :status and p.league.season.archivedAt is null")
    List<TeamParticipation> findVisibleByFederationIdAndRosterStatus(String federationId, RosterStatus status);

    @Query("select p from TeamParticipation p where p.league.season.id = :seasonId and p.league.season.archivedAt is null")
    List<TeamParticipation> findVisibleBySeasonId(String seasonId);

    /** All participations of a season (copy-forward source walk -- ignores archived filtering). */
    List<TeamParticipation> findByLeague_Season_Id(String seasonId);

    /** Whether any participation already exists in a season (copy-forward target guard). */
    boolean existsByLeague_Season_Id(String seasonId);

    /** Whether the league still has any participation registered directly against it (league delete guard). */
    boolean existsByLeague_Id(String leagueId);

    /** Whether the group still has any team placed in it (group delete guard). */
    boolean existsByGroup_Id(String groupId);

    /** The teams to pair up when generating a group's fixtures — withdrawn teams excluded. */
    List<TeamParticipation> findByGroup_IdAndStatus(String groupId, ParticipationStatus status);
}
