package de.dtfb.sportshub.backend.group;

/**
 * Thrown when a hard delete is refused because a {@code TeamParticipation} is still placed in
 * this group. Mapped to {@code 409 Conflict} -- move or unplace the team first.
 */
public class GroupDeletionBlockedException extends RuntimeException {

    public GroupDeletionBlockedException(String message) {
        super(message);
    }
}
