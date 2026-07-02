package de.dtfb.sportshub.backend.teamparticipation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TeamParticipationRepository extends JpaRepository<TeamParticipation, String> {

    @Query("select p from TeamParticipation p where p.competition.season.archivedAt is null")
    List<TeamParticipation> findAllVisible();

    @Query("select p from TeamParticipation p where p.id = :id and p.competition.season.archivedAt is null")
    Optional<TeamParticipation> findVisibleById(String id);

    @Query("select p from TeamParticipation p where p.competition.id = :competitionId and p.competition.season.archivedAt is null")
    List<TeamParticipation> findVisibleByCompetitionId(String competitionId);

    @Query("select p from TeamParticipation p where p.competition.season.id = :seasonId and p.competition.season.archivedAt is null")
    List<TeamParticipation> findVisibleBySeasonId(String seasonId);
}
