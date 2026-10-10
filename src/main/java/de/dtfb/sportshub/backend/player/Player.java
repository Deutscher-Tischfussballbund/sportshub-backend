package de.dtfb.sportshub.backend.player;

import de.dtfb.sportshub.backend.base.BaseEntity;
import de.dtfb.sportshub.backend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A competitor record. {@link #user}, if set, is the login identity (see {@link User}) this
 * competitor record belongs to; nullable because a captain-entered athlete may never log in
 * themselves. A rename is tracked via
 * {@link de.dtfb.sportshub.backend.history.EntityHistoryService} rather than duplicating the row.
 */
@Entity
@Table(name = "player")
@Getter
@Setter
public class Player extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String firstName;
    @Column(nullable = false)
    private String lastName;
    /** The current player number ({@code SS-NNNN}, or a Sports-Hub-issued one); older ones live in {@link PlayerNumber}. */
    private String nationalId;
    private String internationalId;
    private String nationality;
    /** Nullable since V19: old imported records may lack it -- see {@link #isComplete()}. */
    private Integer birthYear;

    /**
     * Stored with the divers league side; see {@link PlayerGender} for who sees what. Nullable since
     * V19 -- see {@link #isComplete()}.
     */
    @Enumerated(EnumType.STRING)
    private PlayerGender gender;

    /** National license grade: "A" | "B" | "C" | "D". */
    private String nationalLicense;

    @Column(nullable = false)
    private boolean active = true;

    /**
     * Whether the player may be rostered: birth year and gender are known. "Complete to play, not
     * complete to exist" (docs/28) -- old imported records stay without them, but
     * {@link de.dtfb.sportshub.backend.category.CategoryEligibility} refuses them on roster add/submit.
     */
    public boolean isComplete() {
        return birthYear != null && gender != null;
    }
}
