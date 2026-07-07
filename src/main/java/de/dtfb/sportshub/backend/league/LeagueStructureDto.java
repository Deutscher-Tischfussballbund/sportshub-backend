package de.dtfb.sportshub.backend.league;

import de.dtfb.sportshub.backend.group.GroupState;

import java.util.List;

/**
 * The Tier -> Group subtree of one league, with a live participation count per group. This is the
 * read behind the placement board (add / move / remove teams into groups): groups nest under their
 * tier so a league's tier/group layout renders directly from one call. {@code participationCount}
 * counts placed participations only (a null group = registered-but-unplaced, not counted).
 */
public record LeagueStructureDto(
    String leagueId,
    String name,
    List<TierNode> tiers
) {
    public record TierNode(String id, String name, List<GroupNode> groups) {
    }

    public record GroupNode(
        String id,
        String name,
        GroupState groupState,
        int participationCount
    ) {
    }
}
