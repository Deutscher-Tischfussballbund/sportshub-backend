package de.dtfb.sportshub.backend.round;

import de.dtfb.sportshub.backend.phase.Phase;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Getter
@Setter
public class Round {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, updatable = false)
    private UUID uuid;

    @ManyToOne
    @JoinColumn(name = "phase_id")
    private Phase phase;

    private String name;
    private Integer index;
}
