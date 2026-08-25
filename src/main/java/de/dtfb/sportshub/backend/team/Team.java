package de.dtfb.sportshub.backend.team;

import de.dtfb.sportshub.backend.base.BaseEntity;
import de.dtfb.sportshub.backend.club.Club;
import de.dtfb.sportshub.backend.season.Season;
import de.dtfb.sportshub.backend.util.IdGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import lombok.Getter;
import lombok.Setter;

/**
 * A team, season-scoped like {@link de.dtfb.sportshub.backend.league.League}: copy-forward clones a
 * fresh row per season (see {@link de.dtfb.sportshub.backend.teamparticipation.CopyForwardService}),
 * so a rename only ever touches the row for the season it's called on -- a past season's history
 * can't be rewritten by a later rename. {@link #teamIdentityId} stays the same across every one of a
 * team's season-copies and is what ties its history/stats/role-scoping together over time.
 */
@Entity
@Getter
@Setter
public class Team extends BaseEntity {
    @ManyToOne
    @JoinColumn(name = "season_id")
    private Season season;

    @ManyToOne
    @JoinColumn(name = "club_id")
    private Club club;

    private String name;

    /** Stable across every season-copy of this team; generated once, carried forward verbatim by copy-forward. */
    @Column(nullable = false, updatable = false)
    private String teamIdentityId;

    /** The team row this was cloned from (copy-forward, or a returning team's re-registration); audit only, not a FK. */
    private String copiedFromTeamId;

    @PrePersist
    void generateIdentity() {
        if (teamIdentityId == null) {
            teamIdentityId = IdGenerator.newId();
        }
    }
}
