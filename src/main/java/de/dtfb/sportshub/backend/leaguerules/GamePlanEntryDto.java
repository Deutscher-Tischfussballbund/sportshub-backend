package de.dtfb.sportshub.backend.leaguerules;

import de.dtfb.sportshub.backend.match.MatchType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GamePlanEntryDto {
    private Integer position;
    private MatchType gameType;
}
