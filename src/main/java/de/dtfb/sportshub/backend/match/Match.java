package de.dtfb.sportshub.backend.match;

import de.dtfb.sportshub.backend.matchday.Matchday;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
public class Match {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, updatable = false)
    private UUID uuid;

    @ManyToOne
    @JoinColumn(name = "matchday_id")
    private Matchday matchday;

    @Column(nullable = false)
    private Instant startTime;

    private Instant endTime;

    private Integer homeScore;

    private Integer awayScore;

    @Enumerated(EnumType.STRING)
    private MatchState state;

    @Enumerated(EnumType.STRING)
    private MatchType type;
}
