package de.dtfb.sportshub.backend.discipline;

import de.dtfb.sportshub.backend.base.BaseEntity;
import de.dtfb.sportshub.backend.category.Category;
import de.dtfb.sportshub.backend.event.Event;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Discipline extends BaseEntity {
    @ManyToOne
    @JoinColumn(name = "event_id")
    private Event event;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;
}
