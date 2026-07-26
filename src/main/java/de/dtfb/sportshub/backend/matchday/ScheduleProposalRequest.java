package de.dtfb.sportshub.backend.matchday;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class ScheduleProposalRequest {
    private Instant startDate;
    private String locationId;
}
