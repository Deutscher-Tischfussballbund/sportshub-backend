package de.dtfb.sportshub.backend.player;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * The next number the Sports Hub issues under one prefix ({@link PlayerNumberKind#prefix()}). A table
 * rather than a DB sequence so it works the same on H2 (dev) and MySQL; read with a pessimistic lock.
 */
@Entity
@Table(name = "player_number_sequence")
@Getter
@Setter
public class PlayerNumberSequence {

    @Id
    @Column(length = 8)
    private String prefix;

    @Column(nullable = false)
    private long nextValue;
}
