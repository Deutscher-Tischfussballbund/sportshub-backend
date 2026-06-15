package de.dtfb.sportshub.backend.standing;

import de.dtfb.sportshub.backend.pool.Pool;
import de.dtfb.sportshub.backend.team.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StandingRepository extends JpaRepository<Standing, String> {
    List<Standing> findByPoolOrderByPointsDescSetsWonDesc(Pool pool);
    Optional<Standing> findByPoolAndTeam(Pool pool, Team team);
}
