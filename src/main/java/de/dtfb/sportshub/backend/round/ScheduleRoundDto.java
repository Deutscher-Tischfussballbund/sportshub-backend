package de.dtfb.sportshub.backend.round;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

/** One round of a {@link ScheduleDto}; fixtures ordered by kick-off, then home team. */
@Getter
@Setter
public class ScheduleRoundDto {
    private String id;
    private String name;
    private Integer index;
    private Instant windowStart;
    private Instant windowEnd;
    private List<ScheduleFixtureDto> fixtures;
}
