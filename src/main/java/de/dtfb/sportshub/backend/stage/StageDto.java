package de.dtfb.sportshub.backend.stage;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class StageDto {
    private UUID id;
    private String name;
    private UUID disciplineId;
}
