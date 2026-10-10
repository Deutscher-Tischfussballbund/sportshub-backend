package de.dtfb.sportshub.backend.importer;

import java.util.List;

/** 409 body of a refused undo. */
public record ImportUndoBlockedError(String code, String message, List<UndoBlocker> blockers) {
}
