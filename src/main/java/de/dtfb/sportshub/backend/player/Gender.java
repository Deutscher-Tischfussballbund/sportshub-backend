package de.dtfb.sportshub.backend.player;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * A player's gender as shown to everyone -- {@link PlayerGender} collapsed so the divers league
 * side stays internal (see {@link PlayerGender#toPublic()}).
 */
public enum Gender {
    MALE("male"),
    FEMALE("female"),
    DIVERSE("diverse");

    private final String value;

    Gender(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static Gender fromValue(String value) {
        for (Gender gender : values()) {
            if (gender.value.equals(value)) {
                return gender;
            }
        }
        throw new IllegalArgumentException("Unknown gender: " + value);
    }
}
