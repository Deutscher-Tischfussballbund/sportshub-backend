package de.dtfb.sportshub.backend.lineup;

import de.dtfb.sportshub.backend.matchday.ResultActor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** Request bodies of the line-up endpoints (docs/23). */
public final class LineupRequests {

    private LineupRequests() {
    }

    /** A team's line-up: the players per game; {@code submit} = final (checked against the rules). */
    @Getter
    @Setter
    public static class SaveLineup {
        private List<GameEntry> games;
        private boolean submit;
    }

    @Getter
    @Setter
    public static class GameEntry {
        private String matchId;
        private List<String> playerIds;
    }

    /** From game {@code matchId} on, {@code playerInId} replaces {@code playerOutId} on {@code side}. */
    @Getter
    @Setter
    public static class Substitute {
        private ResultActor.Side side;
        private String matchId;
        private String playerOutId;
        private String playerInId;
    }
}
