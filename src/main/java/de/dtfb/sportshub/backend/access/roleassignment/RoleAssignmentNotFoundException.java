package de.dtfb.sportshub.backend.access.roleassignment;

import de.dtfb.sportshub.backend.exception.NotFoundExceptionMarker;

public class RoleAssignmentNotFoundException extends NotFoundExceptionMarker {
    public RoleAssignmentNotFoundException(String id) {
        super("role assignment", "ROLE_ASSIGNMENT_NOT_FOUND", id);
    }
}
