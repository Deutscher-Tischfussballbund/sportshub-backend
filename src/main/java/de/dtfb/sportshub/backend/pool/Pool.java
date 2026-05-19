package de.dtfb.sportshub.backend.pool;

import de.dtfb.sportshub.backend.base.BaseEntity;
import de.dtfb.sportshub.backend.stage.Stage;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Pool extends BaseEntity {
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
