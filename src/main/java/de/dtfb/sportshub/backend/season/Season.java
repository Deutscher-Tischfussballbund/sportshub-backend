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

    /**
     * Registration window (team roster management). {@code registrationOpensAt} is required for
     * registration to ever be open -- an admin must deliberately schedule it, matching the old
     * boolean's default-closed behaviour. {@code registrationClosesAt} is optional: leaving it
     * unset means registration stays open indefinitely once it starts.
     */
    private LocalDate registrationOpensAt;
    private LocalDate registrationClosesAt;

    /** Soft-delete marker: null = active; set = archived (hidden from active views, data kept). */
    private Instant archivedAt;

    /**
     * Whether team registration (roster management) is open right now. Replaces the old manually
     * toggled {@code registrationOpen} boolean, which could go stale (still {@code true} long after
     * the window had actually closed) -- this is derived fresh from the window on every read.
     */
    public boolean isRegistrationOpen() {
        LocalDate today = LocalDate.now();
        if (registrationOpensAt == null || today.isBefore(registrationOpensAt)) {
            return false;
        }
        return registrationClosesAt == null || !today.isAfter(registrationClosesAt);
    }
}
