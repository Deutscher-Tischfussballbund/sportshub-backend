package de.dtfb.sportshub.backend.round;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoundDto {
    private String id;
    private String name;
    private String poolId;
    private Integer index;
}
