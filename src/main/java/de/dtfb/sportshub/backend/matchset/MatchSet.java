package de.dtfb.sportshub.backend.matchset;

import de.dtfb.sportshub.backend.base.BaseEntity;
import de.dtfb.sportshub.backend.match.Match;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class MatchSet extends BaseEntity {
    @ManyToOne
    @JoinColumn(name = "match_id")
    private Match match;

    private Integer setNumber;

    private Integer homeScore;

    private Integer awayScore;
}
