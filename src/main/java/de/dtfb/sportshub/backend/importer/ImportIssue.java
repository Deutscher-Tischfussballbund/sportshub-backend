package de.dtfb.sportshub.backend.importer;

/** One issue of a planned record; {@code detail} carries ids or values for the message (e.g. candidate player ids). */
public record ImportIssue(ImportIssueCode code, String detail) {

    public static ImportIssue of(ImportIssueCode code) {
        return new ImportIssue(code, null);
    }
}
