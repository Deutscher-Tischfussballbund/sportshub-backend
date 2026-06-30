package de.dtfb.sportshub.backend.competition;

import de.dtfb.sportshub.backend.base.BaseEntity;
import de.dtfb.sportshub.backend.season.Season;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Competition extends BaseEntity {
    @ManyToOne
    @JoinColumn(name = "season_id")
    private Season season;

    private String name;

    private String importId;
}
