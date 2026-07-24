package de.dtfb.sportshub.backend.access.role;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Kind of scope a {@link de.dtfb.sportshub.backend.access.roleassignment.RoleAssignment} applies to. Wire values match the frontend. */
public enum ScopeType {
    GLOBAL("global"),
    REGION("region"),
    CLUB("club"),
    TEAM("team"),
    /**
     * A single league. Maps to {@code League.id}. Named LEAGUE rather than the more generic
     * COMPETITION because leagues are the only thing scoped this way today (tournaments are
     * parked); revisit once a first-class Competition concept unifies both.
     */
    LEAGUE("league");

    private final String value;

    ScopeType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ScopeType fromValue(String value) {
        for (ScopeType scopeType : values()) {
            if (scopeType.value.equals(value)) {
                return scopeType;
            }
        }
        throw new IllegalArgumentException("Unknown scope type: " + value);
    }
}
