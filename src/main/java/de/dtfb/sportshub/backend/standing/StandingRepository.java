package de.dtfb.sportshub.backend.standing;

import de.dtfb.sportshub.backend.group.Group;
import de.dtfb.sportshub.backend.team.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StandingRepository extends JpaRepository<Standing, String> {
    List<Standing> findByGroupOrderByPointsDescSetsWonDesc(Group group);
    Optional<Standing> findByGroupAndTeam(Group group, Team team);
}
