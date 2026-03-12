package de.dtfb.sportshub.backend.importer.data;

import de.dtfb.sportshub.backend.match.MatchState;
import de.dtfb.sportshub.backend.match.MatchType;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
public class ImportMatch {
    private Instant startDate;
    private Instant endDate;
    private MatchType type;
    private MatchState state;
    private List<ImportPlayer> players;
    private List<ImportSet> sets;
}
