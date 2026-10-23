package de.dtfb.sportshub.backend.knockout;

import de.dtfb.sportshub.backend.round.FixtureSlot;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

/**
 * Body of {@code POST /v1/tiers/{id}/knockout-stage} (docs/34): creates a knock-out group in the tier with
 * source-based fixtures in the usual shapes. Everything it creates stays editable until results exist.
 */
@Getter
@Setter
public class KnockoutStageRequest {
    /** Name of the knock-out group; default "K.O.-Runde". */
    private String name;
    /** One or two round-robin groups of the tier that feed the bracket. */
    private List<String> sourceGroupIds;
    /** Teams per source group in the main bracket; groups × this must be 2, 4, 8 or 16. */
    private int qualifiersPerGroup;
    /** The first group place that qualifies (default 1; e.g. 9 for a playdown bracket of places 9–12). */
    private Integer fromPlace;
    /** A game for 3rd place between the semi-final losers. */
    private boolean thirdPlaceGame;
    /** Placement games for the group places after the qualifiers (two source groups only). */
    private boolean placementGames;
    /** Rules of the knock-out group (e.g. the K.O. blueprint, no draw); null = the tier's/league's rules. */
    private String blueprintId;
    /** One slot per round (kick-off, venue), as for tournament weekends (docs/12); or all at {@link #startDate}. */
    private List<FixtureSlot> slots;
    private Instant startDate;
}
