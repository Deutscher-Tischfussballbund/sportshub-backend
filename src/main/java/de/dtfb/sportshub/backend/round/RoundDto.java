package de.dtfb.sportshub.backend.round;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class RoundDto {
    private UUID uuid;
    private String name;
    private UUID phaseUuid;
    private Integer index;
}
