package de.dtfb.sportshub.backend.standing;

import de.dtfb.sportshub.backend.group.Group;
import de.dtfb.sportshub.backend.team.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StandingRepository extends JpaRepository<Standing, String> {
    List<Standing> findByGroupOrderByPointsDescSetsWonDesc(Group group);
    Optional<Standing> findByGroupAndTeam(Group group, Team team);

    /** Whether the team has a recorded standing anywhere in this league. */
    @Query("select count(s) > 0 from Standing s where s.group.tier.league.id = :leagueId and s.team.id = :teamId")
    boolean existsByLeagueIdAndTeamId(@Param("leagueId") String leagueId, @Param("teamId") String teamId);
}
