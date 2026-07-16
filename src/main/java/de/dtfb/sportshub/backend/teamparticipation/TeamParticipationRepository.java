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
}
