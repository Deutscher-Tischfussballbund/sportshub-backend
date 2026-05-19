package de.dtfb.sportshub.backend.discipline;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DisciplineDto {
    private String id;
    private String eventId;
    private String name;
    private String shortName;
}
