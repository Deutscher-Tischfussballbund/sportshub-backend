package de.dtfb.sportshub.backend.access.role;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Roles a player can hold, mirroring the admin frontend's role model.
 * The wire value (e.g. {@code region_admin}) is what the OpenAPI client sends/expects;
 * {@link #name()} (e.g. {@code REGION_ADMIN}) is what JPA stores.
 */
public enum Role {
    // --- Org-administration backbone (scope = federation tree) ---
    ADMIN("admin", ScopeType.GLOBAL),
    REGION_ADMIN("region_admin", ScopeType.REGION),
    CLUB_ADMIN("club_admin", ScopeType.CLUB),
    /** Acts for a single team, e.g. submits/confirms league match-day results. */
    TEAM_ADMIN("team_admin", ScopeType.TEAM),

    // --- Functional roles (scope = the competition) ---
    /** Runs one competition (league or tournament): structure, scheduling, results, standings. */
    COMPETITION_ORGANIZER("competition_organizer", ScopeType.COMPETITION),

    // --- Deprecated: superseded by COMPETITION_ORGANIZER (wrong axis — anchored to club/region,
    // not the competition). Kept until removal is coordinated with the @dtfb/api client + i18n. ---
    @Deprecated
    TOURNAMENT_UPLOADER("tournament_uploader", ScopeType.CLUB),
    @Deprecated
    REGION_TOURNAMENT_UPLOADER("region_tournament_uploader", ScopeType.REGION);

    private final String value;
    private final ScopeType scopeType;

    Role(String value, ScopeType scopeType) {
        this.value = value;
        this.scopeType = scopeType;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    /** The scope kind a grant of this role implies (drives where scopeId points). */
    public ScopeType scopeType() {
        return scopeType;
    }

    @JsonCreator
    public static Role fromValue(String value) {
        for (Role role : values()) {
            if (role.value.equals(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown role: " + value);
    }
}
