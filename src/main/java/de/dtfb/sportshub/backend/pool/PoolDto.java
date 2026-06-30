package de.dtfb.sportshub.backend.pool;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PoolDto {
    private String id;
    private String name;
    private String stageId;

    @NotNull
    private TournamentMode tournamentMode;
    @NotNull
    private PoolState poolState;
}
