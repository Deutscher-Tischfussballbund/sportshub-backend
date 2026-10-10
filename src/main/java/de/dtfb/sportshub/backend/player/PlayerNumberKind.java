package de.dtfb.sportshub.backend.player;

/**
 * Where a {@link PlayerNumber} comes from (docs/24, docs/28). Real numbers ({@code SS-NNNN}, SS = the
 * federal state) are issued by the DTFB/Sports Manager; the Sports Hub issues the others itself, with a
 * prefix that tells the reason.
 */
public enum PlayerNumberKind {
    /** Issued by the DTFB, carried over from the Sports Manager. */
    REAL(null),
    /** Issued by the Sports Hub: a DTFB member whose number is missing in the Sports Manager. */
    NOT_GIVEN("NG"),
    /** Issued by the Sports Hub: a player outside the DTFB (hobby/pub leagues). Prefix open, SPO-117. */
    HOBBY("XX");

    private final String prefix;

    PlayerNumberKind(String prefix) {
        this.prefix = prefix;
    }

    /** The prefix the Sports Hub issues this kind under; null for {@link #REAL}. */
    public String prefix() {
        return prefix;
    }
}
