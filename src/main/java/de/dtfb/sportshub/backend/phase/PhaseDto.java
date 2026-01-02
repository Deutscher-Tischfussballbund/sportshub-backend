package de.dtfb.sportshub.backend.phase;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class PhaseDto {
    private UUID uuid;
    private String name;
    private UUID stageUuid;

    @NotNull
    private TournamentMode tournamentMode;
    @NotNull
    private PhaseState phaseState;
}
