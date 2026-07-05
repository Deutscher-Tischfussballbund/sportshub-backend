package de.dtfb.sportshub.backend.competition;

import de.dtfb.sportshub.backend.pool.PoolState;
import de.dtfb.sportshub.backend.pool.TournamentMode;

import java.util.List;

/**
 * The Discipline → Stage → Pool subtree of one competition, with a live participation count per pool.
 * This is the read behind the placement board (add / move / remove teams into pools): pools nest under
 * their stage and discipline so a league's tier/group layout — or a tournament's group/bracket layout —
 * renders directly from one call. A league tier ("1. Bayernliga") is not a node; it lives in the pool
 * name (see docs/01 §1.1). {@code participationCount} counts placed participations only (a null pool =
 * registered-but-unplaced, not counted).
 */
public record CompetitionStructureDto(
    String competitionId,
    String name,
    List<DisciplineNode> disciplines
) {
    public record DisciplineNode(String id, String categoryId, List<StageNode> stages) {
    }

    public record StageNode(String id, String name, List<PoolNode> pools) {
    }

    public record PoolNode(
        String id,
        String name,
        TournamentMode tournamentMode,
        PoolState poolState,
        int participationCount
    ) {
    }
}
