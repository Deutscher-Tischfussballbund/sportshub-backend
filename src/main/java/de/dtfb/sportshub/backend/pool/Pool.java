package de.dtfb.sportshub.backend.pool;

import de.dtfb.sportshub.backend.stage.Stage;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Getter
@Setter
public class Pool {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, updatable = false)
    private UUID uuid;

    @ManyToOne
    @JoinColumn(name = "stage_id")
    private Stage stage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TournamentMode tournamentMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PoolState poolState;

    private String name;
}
