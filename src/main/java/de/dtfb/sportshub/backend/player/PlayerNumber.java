package de.dtfb.sportshub.backend.player;

import de.dtfb.sportshub.backend.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A number a player holds or held. Numbers are linked, never overwritten (docs/24, docs/28): when a
 * player gets a new number (a real one replacing an {@code NG-} number, or a hobby player joining the
 * DTFB) the old row gets {@link #validTo} and stays findable as an alias. The current number is the row
 * without {@code validTo}, mirrored in {@link Player#getNationalId()}. A number belongs to one player
 * for good -- unique across current and old numbers.
 */
@Entity
@Table(name = "player_number")
@Getter
@Setter
public class PlayerNumber extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "player_id")
    private Player player;

    @Column(nullable = false, unique = true, length = 32)
    private String number;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PlayerNumberKind kind;

    @Column(nullable = false)
    private Instant validFrom;

    /** Null while this is the player's current number. */
    private Instant validTo;
}
