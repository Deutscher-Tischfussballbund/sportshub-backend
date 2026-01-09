package de.dtfb.sportshub.backend.event;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class EventDto {
    private UUID id;
    private String name;
    private UUID seasonId;
}
