package de.dtfb.sportshub.backend.event;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EventDto {
    private String id;
    private String name;
    private String seasonId;
}
