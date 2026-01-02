package de.dtfb.sportshub.backend.stage;

import de.dtfb.sportshub.backend.discipline.Discipline;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Getter
@Setter
public class Stage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, updatable = false)
    private UUID uuid;

    @ManyToOne
    @JoinColumn(name = "discipline_id")
    private Discipline discipline;

    private String name;
}
