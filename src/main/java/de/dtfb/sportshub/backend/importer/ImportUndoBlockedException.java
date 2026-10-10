package de.dtfb.sportshub.backend.importer;

import lombok.Getter;

import java.util.List;

/** Undo refused (docs/28); mapped to 409 IMPORT_UNDO_BLOCKED with the blockers. */
@Getter
public class ImportUndoBlockedException extends RuntimeException {

    private final List<UndoBlocker> blockers;

    public ImportUndoBlockedException(List<UndoBlocker> blockers) {
        super("The import can't be undone");
        this.blockers = List.copyOf(blockers);
    }
}
