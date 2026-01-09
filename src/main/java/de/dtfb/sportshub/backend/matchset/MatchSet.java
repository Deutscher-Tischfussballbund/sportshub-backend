package de.dtfb.sportshub.backend.matchset;

import de.dtfb.sportshub.backend.match.Match;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Getter
@Setter
public class MatchSet {
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "match_id")
    private Match match;

    private Integer setNumber;

    private Integer homeScore;

    private Integer awayScore;
}
