package de.dtfb.sportshub.backend.season;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class SeasonDto {
    private UUID uuid;
    private String name;
}
