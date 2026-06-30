package de.dtfb.sportshub.backend.season;

import de.dtfb.sportshub.backend.base.BaseEntity;
import de.dtfb.sportshub.backend.federation.Federation;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Getter
@Setter
public class Season extends BaseEntity {
    private String name;

    @ManyToOne
    @JoinColumn(name = "federation_id")
    private Federation federation;

    // General duration of the season.
    private LocalDate startDate;
    private LocalDate endDate;

    /** Whether team registration (roster management) is open for this season. */
    private boolean registrationOpen;

    /** Soft-delete marker: null = active; set = archived (hidden from active views, data kept). */
    private Instant archivedAt;
}
