package de.dtfb.sportshub.backend.season;

/**
 * Thrown when a hard delete is refused because the season holds recorded results. Mapped to
 * {@code 409 Conflict} with a structured body (code + message + {@link SeasonContents}) so the
 * frontend can explain the block and offer to archive instead.
 */
public class SeasonDeletionBlockedException extends RuntimeException {

    private final transient SeasonContents contents;

    public SeasonDeletionBlockedException(SeasonContents contents) {
        super("Season has recorded results and cannot be deleted; archive it instead.");
        this.contents = contents;
    }

    public SeasonContents getContents() {
        return contents;
    }
}
