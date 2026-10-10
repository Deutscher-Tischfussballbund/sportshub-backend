package de.dtfb.sportshub.backend.importer;

import java.util.List;

/** What undoing a run would do, or why it can't (docs/28). */
public record UndoCheckDto(boolean possible, int created, int changed, List<UndoBlocker> blockers) {
}
