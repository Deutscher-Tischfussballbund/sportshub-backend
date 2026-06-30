package de.dtfb.sportshub.backend.importer.data;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
public class ImportMatchDay {
    private String name;
    private Instant startDate;
    private Instant endDate;
    private ImportTeam teamHome;
    private ImportTeam teamAway;
    private List<ImportMatch> matches;
}
