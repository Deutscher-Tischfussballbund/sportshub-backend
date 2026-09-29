package de.dtfb.sportshub.backend.matchevent;

import de.dtfb.sportshub.backend.base.BaseEntity;
import de.dtfb.sportshub.backend.match.Match;
import de.dtfb.sportshub.backend.player.Player;
import de.dtfb.sportshub.backend.team.Team;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
public class MatchEvent extends BaseEntity {
    @ManyToOne
    @JoinColumn(name = "match_id")
    private Match match;

    @ManyToOne
    @JoinColumn(name = "team_id")
    private Team team;

    /** The player of a single-player event (goal, card). */
    @ManyToOne
    @JoinColumn(name = "player_id")
    private Player player;

    /** SUBSTITUTION (docs/23): who comes in and who goes out, from this event's game on. */
    @ManyToOne
    @JoinColumn(name = "player_in_id")
    private Player playerIn;

    @ManyToOne
    @JoinColumn(name = "player_out_id")
    private Player playerOut;

    private Instant timestamp;

    @Enumerated(EnumType.STRING)
    private MatchEventType type;

    private Integer homeScore;
    private Integer awayScore;

    // Stored as plain text; the JSON payload is (de)serialized in MatchEventMapper, not the DB.
    // (A native "json" column re-encodes the stored string, corrupting the round-trip.)
    @Lob
    private String json;
}
