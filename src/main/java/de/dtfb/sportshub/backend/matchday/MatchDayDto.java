package de.dtfb.sportshub.backend.matchday;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class MatchDayDto {
    private UUID id;
    private String name;
    private UUID roundId;
    private UUID locationId;
    private UUID teamAwayId;
    private UUID teamHomeId;
    private Instant startDate;
    private Instant endDate;
}
