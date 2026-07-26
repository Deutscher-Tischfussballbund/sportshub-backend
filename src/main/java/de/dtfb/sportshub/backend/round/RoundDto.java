package de.dtfb.sportshub.backend.round;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class RoundDto {
    private String id;
    private String name;
    private String groupId;
    private Integer index;
    private Instant windowStart;
    private Instant windowEnd;
}
