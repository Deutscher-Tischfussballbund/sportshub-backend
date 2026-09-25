package de.dtfb.sportshub.backend.player;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The side of the men's/women's league split a player competes on (see
 * {@link PlayerGender#leagueSide()}), and the side a {@code Category} may restrict its leagues to.
 */
public enum LeagueSide {
    MEN("men"),
    WOMEN("women");

    private final String value;

    LeagueSide(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static LeagueSide fromValue(String value) {
        for (LeagueSide side : values()) {
            if (side.value.equals(value)) {
                return side;
            }
        }
        throw new IllegalArgumentException("Unknown league side: " + value);
    }
}
