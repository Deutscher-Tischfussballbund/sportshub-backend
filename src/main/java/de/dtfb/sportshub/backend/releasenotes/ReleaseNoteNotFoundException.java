package de.dtfb.sportshub.backend.releasenotes;

import de.dtfb.sportshub.backend.exception.NotFoundExceptionMarker;

public class ReleaseNoteNotFoundException extends NotFoundExceptionMarker {
    public ReleaseNoteNotFoundException(String id) {
        super("releaseNote", "RELEASE_NOTE_NOT_FOUND", id);
    }
}
