package de.dtfb.sportshub.backend.matchday;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class MatchdayDto {
    private UUID uuid;
    private String name;
    private UUID roundUuid;
    private UUID locationUuid;
    private UUID teamAwayUuid;
    private UUID teamHomeUuid;
    private Instant startDate;
    private Instant endDate;
}
