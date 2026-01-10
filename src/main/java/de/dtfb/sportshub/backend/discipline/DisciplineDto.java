package de.dtfb.sportshub.backend.discipline;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class DisciplineDto {
    private UUID id;
    private UUID eventId;
    private String name;
    private String shortName;
}
