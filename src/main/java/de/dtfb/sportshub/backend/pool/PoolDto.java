package de.dtfb.sportshub.backend.pool;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class PoolDto {
    private UUID id;
    private String name;
    private UUID stageId;

    @NotNull
    private TournamentMode tournamentMode;
    @NotNull
    private PoolState poolState;
}
