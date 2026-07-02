package de.dtfb.sportshub.backend.roster;

import de.dtfb.sportshub.backend.base.BaseEntity;
import de.dtfb.sportshub.backend.player.Player;
import de.dtfb.sportshub.backend.teamparticipation.TeamParticipation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * One player's membership on a team's roster for a participation (L2). First-class and auditable:
 * removal is a soft-delete ({@link #removedAt} set, row kept) so transfer/roster history stays
 * answerable. Active roster = entries with {@code removedAt == null}.
 */
@Entity
@Getter
@Setter
public class RosterEntry extends BaseEntity {
    @ManyToOne
    @JoinColumn(name = "participation_id")
    private TeamParticipation participation;

    @ManyToOne
    @JoinColumn(name = "player_id")
    private Player player;

    @Column(nullable = false)
    private Instant addedAt;

    /** Null while the player is on the roster; set when removed (soft-delete). */
    private Instant removedAt;
}
