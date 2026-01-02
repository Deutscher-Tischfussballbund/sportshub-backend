package de.dtfb.sportshub.backend.matchset;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class MatchsetDto {
    private UUID uuid;
    private UUID matchUuid;
    private Integer setNumber;
    private Integer homeScore;
    private Integer awayScore;
}
