package de.dtfb.sportshub.backend.clubmembership;

import de.dtfb.sportshub.backend.base.BaseEntity;
import de.dtfb.sportshub.backend.club.Club;
import de.dtfb.sportshub.backend.player.Player;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A player's membership in a club -- the precondition for being rostered onto one of that club's
 * teams (see {@link de.dtfb.sportshub.backend.roster.RosterService#addPlayer}). First-class and
 * auditable, same shape as {@link de.dtfb.sportshub.backend.roster.RosterEntry}: leaving a club is
 * a soft-delete ({@link #leftAt} set, row kept), not a hard delete. A player may hold several
 * active memberships at once (multiple clubs).
 */
@Entity
@Getter
@Setter
public class ClubMembership extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "player_id")
    private Player player;

    @ManyToOne
    @JoinColumn(name = "club_id")
    private Club club;

    @Column(nullable = false)
    private Instant joinedAt;

    /** Null while the membership is active; set when the player leaves the club (soft-delete). */
    private Instant leftAt;
}
