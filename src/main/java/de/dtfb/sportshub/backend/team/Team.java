package de.dtfb.sportshub.backend.team;

import de.dtfb.sportshub.backend.base.BaseEntity;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Team extends BaseEntity {
    private String name;

    //TODO link to federation? since name is not unique
}
