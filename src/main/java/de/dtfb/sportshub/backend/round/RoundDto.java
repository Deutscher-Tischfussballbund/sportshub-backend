package de.dtfb.sportshub.backend.round;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class RoundDto {
    private UUID id;
    private String name;
    private UUID poolId;
    private Integer index;
}
