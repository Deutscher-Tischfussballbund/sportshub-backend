package de.dtfb.sportshub.backend.team;

import de.dtfb.sportshub.backend.season.Season;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, String> {
    Optional<Team> findByName(String name);

    List<Team> findBySeasonId(String seasonId);

    List<Team> findByTeamIdentityId(String teamIdentityId);

    Optional<Team> findByTeamIdentityIdAndSeason(String teamIdentityId, Season season);

    /** Whether the club still has any team (any season) -- club delete guard. */
    boolean existsByClub_Id(String clubId);
}
