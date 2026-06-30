package de.dtfb.sportshub.backend.pool;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TournamentMode {
    DOUBLE_ELIMINATION("double elimination"),
    DUTCH_SYSTEM("dutch system"),
    ELIMINATION("elimination"),
    LAST_ONE_STANDING("last one standing"),
    LORD_HAVE_MERCY("lord have mercy"),
    MONSTER_DYP("monster dyp"),
    ROUND_ROBIN("round robin"),
    ROUNDS("rounds"),
    SNAKE_DRAW("snake draw"),
    SWISS("swiss"),
    UNKNOWN("unknown"),
    WHIST("whist");

    private final String value;

    TournamentMode(String value) {
        this.value = value;
    }

    @JsonCreator
    public static TournamentMode fromValue(String value) {
        for (TournamentMode mode : values()) {
            if (mode.value.equalsIgnoreCase(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown mode: " + value);
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
