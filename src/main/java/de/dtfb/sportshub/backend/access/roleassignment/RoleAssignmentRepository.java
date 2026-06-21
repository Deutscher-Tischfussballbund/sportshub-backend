package de.dtfb.sportshub.backend.access.roleassignment;

import de.dtfb.sportshub.backend.access.role.Role;
import de.dtfb.sportshub.backend.player.Player;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoleAssignmentRepository extends JpaRepository<RoleAssignment, String> {

    List<RoleAssignment> findByPlayer(Player player);

    List<RoleAssignment> findByRole(Role role);

    List<RoleAssignment> findByPlayer_Id(String playerId);

    List<RoleAssignment> findByRoleAndPlayer_Id(Role role, String playerId);

    boolean existsByPlayerAndRoleAndScopeId(Player player, Role role, String scopeId);
}
