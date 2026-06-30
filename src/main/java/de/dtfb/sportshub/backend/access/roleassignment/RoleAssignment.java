package de.dtfb.sportshub.backend.access.roleassignment;
import de.dtfb.sportshub.backend.access.role.Role;
import de.dtfb.sportshub.backend.access.role.ScopeType;

import de.dtfb.sportshub.backend.base.BaseEntity;
import de.dtfb.sportshub.backend.player.Player;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A role granted to a {@link Player}, optionally scoped to a federation/club/team.
 * Nano-id keyed via {@link BaseEntity}.
 */
@Entity
@Table(name = "role_assignment")
@Getter
@Setter
public class RoleAssignment extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "player_id")
    private Player player;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScopeType scopeType;

    /** Federation id, club id, or team id depending on {@link #scopeType}; null for GLOBAL. */
    private String scopeId;

    /** dtfb_id of the granting player, if known. */
    private String grantedByDtfbId;

    @Column(nullable = false)
    private Instant createdAt;
}
