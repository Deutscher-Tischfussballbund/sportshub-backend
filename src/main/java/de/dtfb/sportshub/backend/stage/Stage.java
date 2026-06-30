package de.dtfb.sportshub.backend.stage;

import de.dtfb.sportshub.backend.base.BaseEntity;
import de.dtfb.sportshub.backend.discipline.Discipline;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Stage extends BaseEntity {
    @ManyToOne
    @JoinColumn(name = "discipline_id")
    private Discipline discipline;

    private String name;
}
