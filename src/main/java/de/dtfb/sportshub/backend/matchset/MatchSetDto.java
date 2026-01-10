package de.dtfb.sportshub.backend.matchset;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class MatchSetDto {
    private UUID id;
    private UUID matchId;
    private Integer setNumber;
    private Integer homeScore;
    private Integer awayScore;
}
