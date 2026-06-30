package de.dtfb.sportshub.backend.season;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
public class SeasonDto {
    private String id;
    private String name;
    private String federationId;

    // General duration.
    private LocalDate startDate;
    private LocalDate endDate;

    // Registration window.
    private boolean registrationOpen;
    private Instant registrationOpensAt;
    private Instant registrationClosesAt;

    /** Derived (not stored): the effective registration state right now. Read-only. */
    @JsonProperty("isOpen")
    private boolean open;
}
