package de.dtfb.sportshub.backend.access.role;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Kind of scope a {@link de.dtfb.sportshub.backend.access.roleassignment.RoleAssignment} applies to. Wire values match the frontend. */
public enum ScopeType {
    GLOBAL("global"),
    REGION("region"),
    CLUB("club"),
    TEAM("team"),
    /** A single competition (league or tournament). Maps to {@code Competition.id}. */
    COMPETITION("competition");

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
