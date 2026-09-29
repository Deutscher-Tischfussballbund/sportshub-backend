package de.dtfb.sportshub.backend.lineup;

import de.dtfb.sportshub.backend.match.MatchType;
import de.dtfb.sportshub.backend.matchday.ResultActor;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

/**
 * Both line-ups of a fixture as the current user may see them (docs/23): their own side always, the
 * other once both have submitted or kick-off has passed; neutral admins see both. {@code games} holds
 * who actually plays each game -- the line-up with the substitutions applied.
 */
@Getter
@Setter
public class LineupsDto {
    private String matchDayId;
    /** The rule set requires both line-ups before a team can enter a result. */
    private boolean lineupRequired;
    private boolean bothSubmitted;
    private boolean kickOffPassed;

    /** The line-up rules (null = no limit), so the line-up page can explain them before submitting. */
    private Integer maxGamesPerPlayer;
    private Integer maxSinglesPerPlayer;
    private Integer maxPlayers;
    /** The Race block rule applies (first block of doubles / the rest). */
    private boolean blockRule;
    private Integer maxSubstitutions;

    private List<GameDto> games;
    private SideDto home;
    private SideDto away;
    private List<SubstitutionDto> substitutions;

    /** For the current user. */
    private ResultActor.Side side;
    private boolean neutralAdmin;

    @Getter
    @Setter
    public static class GameDto {
        private String matchId;
        private Integer position;
        private MatchType type;
        /** Who plays, after substitutions; null when that side isn't visible to the current user. */
        private List<PlayerRefDto> homePlayers;
        private List<PlayerRefDto> awayPlayers;
    }

    @Getter
    @Setter
    public static class SideDto {
        private String teamId;
        private String teamName;
        private boolean submitted;
        private Instant submittedAt;
        /** The current user may see this side's line-up. */
        private boolean visible;
        /** The current user may edit this line-up now (own captain before both are in, or a neutral admin). */
        private boolean canEdit;
        /** The current user may record substitutions for this side now. */
        private boolean canSubstitute;
        /** Substitutions this side may still make; null = unlimited. */
        private Integer substitutionsLeft;
        /** The line-up as submitted (or drafted), per game; null when not visible. */
        private List<PlannedDto> planned;
        /** The team's current roster -- only for someone who may edit or substitute. */
        private List<PlayerRefDto> roster;
    }

    @Getter
    @Setter
    public static class PlannedDto {
        private String matchId;
        private List<String> playerIds;
    }

    @Getter
    @Setter
    public static class SubstitutionDto {
        private String id;
        private ResultActor.Side side;
        /** The game it takes effect in (and all later games of the player's position). */
        private String matchId;
        private PlayerRefDto playerIn;
        private PlayerRefDto playerOut;
        private Instant timestamp;
        private boolean canDelete;
    }

    @Getter
    @Setter
    public static class PlayerRefDto {
        private String id;
        private String name;
    }
}
