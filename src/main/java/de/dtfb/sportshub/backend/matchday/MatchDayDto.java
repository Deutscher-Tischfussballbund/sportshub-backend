package de.dtfb.sportshub.backend.matchday;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class MatchDayDto {
    private String id;
    private String name;
    private String roundId;
    private String locationId;
    private String teamAwayId;
    private String teamHomeId;
    private Instant startDate;
    private Instant endDate;
    private ResultState resultState;
    private Instant homeConfirmedAt;
    private Instant awayConfirmedAt;
}
