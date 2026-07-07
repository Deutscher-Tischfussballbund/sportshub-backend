package de.dtfb.sportshub.backend.group;

import de.dtfb.sportshub.backend.exception.NotFoundExceptionMarker;

public class GroupNotFoundException extends NotFoundExceptionMarker {
    public GroupNotFoundException(String id) {
        super("group", "GROUP_NOT_FOUND", id);
    }
}
