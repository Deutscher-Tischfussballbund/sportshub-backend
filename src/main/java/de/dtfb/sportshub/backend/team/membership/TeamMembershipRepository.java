package de.dtfb.sportshub.backend.team.membership;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TeamMembershipRepository extends JpaRepository<TeamMembership, Long> {

    @Query("SELECT tm FROM TeamMembership tm WHERE tm.team.id = :teamId AND tm.role = :role")
    Optional<TeamMembership> findByTeamIdAndRole(@Param("teamId") Long teamId, @Param("role") TeamMembershipEnum role);
}
