package de.dtfb.sportshub.backend.player;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * A player's stored gender. "Divers" is split by the league side the player competes on, because
 * league eligibility needs to know it -- but that side is internal: only admins see it (as
 * {@link PlayerDto#getGenderDetail()}); everyone else sees the collapsed {@link #toPublic()} value.
 * The wire value (e.g. {@code diverse_men}) is what the OpenAPI client sends/expects;
 * {@link #name()} is what JPA stores.
 */
public enum PlayerGender {
    MALE("male", Gender.MALE, LeagueSide.MEN),
    FEMALE("female", Gender.FEMALE, LeagueSide.WOMEN),
    /** Divers, competing in men's leagues ("divers (Herrenligen)"). */
    DIVERSE_MEN("diverse_men", Gender.DIVERSE, LeagueSide.MEN),
    /** Divers, competing in women's leagues ("divers (Damenligen)"). */
    DIVERSE_WOMEN("diverse_women", Gender.DIVERSE, LeagueSide.WOMEN);

    private final String value;
    private final Gender publicGender;
    private final LeagueSide leagueSide;

    PlayerGender(String value, Gender publicGender, LeagueSide leagueSide) {
        this.value = value;
        this.publicGender = publicGender;
        this.leagueSide = leagueSide;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    /** What the outside world sees: both divers variants collapse to {@link Gender#DIVERSE}. */
    public Gender toPublic() {
        return publicGender;
    }

    /** The league side this player competes on -- the basis for future league eligibility filtering. */
    public LeagueSide leagueSide() {
        return leagueSide;
    }

    @JsonCreator
    public static PlayerGender fromValue(String value) {
        for (PlayerGender gender : values()) {
            if (gender.value.equals(value)) {
                return gender;
            }
        }
        throw new IllegalArgumentException("Unknown player gender: " + value);
    }
}
