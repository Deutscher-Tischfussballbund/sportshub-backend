package de.dtfb.sportshub.backend.importer.data;

import de.dtfb.sportshub.backend.pool.PoolState;
import de.dtfb.sportshub.backend.pool.TournamentMode;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ImportGroup {
    private String name;
    private TournamentMode tournamentMode;
    private PoolState state;
    private List<ImportRound> rounds;
}
