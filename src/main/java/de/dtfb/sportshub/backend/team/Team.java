package de.dtfb.sportshub.backend.team;

import de.dtfb.sportshub.backend.base.BaseEntity;
import de.dtfb.sportshub.backend.club.Club;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Team extends BaseEntity {
    private String name;

    @ManyToOne
    @JoinColumn(name = "club_id")
    private Club club;
}
