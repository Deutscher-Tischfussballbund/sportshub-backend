package de.dtfb.sportshub.backend.access.apiclient;

import de.dtfb.sportshub.backend.access.role.ScopeType;
import de.dtfb.sportshub.backend.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Write-permission grant for a registered app/service client, keyed on the JWT's {@code azp}
 * (authorized party) claim -- independent of {@link de.dtfb.sportshub.backend.access.roleassignment.RoleAssignment},
 * which is keyed on a human's {@code dtfb_id}. See docs/10-api-consumers-and-authz.md §5.
 *
 * <p>Authentication (is this JWT from a trusted client at all) is a separate, earlier layer -- the
 * {@code azp} allow-list in {@code JwtDecoderConfig}. This grant only governs what an already-
 * trusted client may additionally do: read is implicit for any allowed client, {@link #writeAccess}
 * unlocks writes within {@link #scopeType}/{@link #scopeId}.
 */
@Entity
@Getter
@Setter
public class ApiClientGrant extends BaseEntity {

    /** The Keycloak client id (JWT {@code azp} claim) this grant applies to. Not a foreign key --
     * Keycloak client provisioning stays a separate, manual step. */
    @Column(nullable = false, unique = true)
    private String clientId;

    /** Human label for the admin UI, e.g. "Public Results Site". */
    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean writeAccess = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScopeType scopeType = ScopeType.GLOBAL;

    /** Federation id, club id, team id, or league id depending on {@link #scopeType}; null for GLOBAL. */
    private String scopeId;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant lastUsedAt;
}
