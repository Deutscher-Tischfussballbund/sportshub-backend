package de.dtfb.sportshub.backend.category;

import de.dtfb.sportshub.backend.player.LeagueSide;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CategoryDto {
    private String id;
    private String name;
    private String shortName;
    /** The league side players must compete on; null = open to every gender. */
    private LeagueSide eligibleSide;
}
