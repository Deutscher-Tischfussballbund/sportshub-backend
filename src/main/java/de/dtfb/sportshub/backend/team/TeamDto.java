package de.dtfb.sportshub.backend.team;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class TeamDto {
    private UUID uuid;
    private String name;
}
