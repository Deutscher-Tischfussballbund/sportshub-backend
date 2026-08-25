package de.dtfb.sportshub.backend.access.roleassignment;

import de.dtfb.sportshub.backend.access.role.Role;
import de.dtfb.sportshub.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoleAssignmentRepository extends JpaRepository<RoleAssignment, String> {

    List<RoleAssignment> findByUser(User user);

    List<RoleAssignment> findByRole(Role role);

    List<RoleAssignment> findByUser_Id(String userId);

    List<RoleAssignment> findByRoleAndUser_Id(Role role, String userId);

}
