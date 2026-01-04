package de.dtfb.sportshub.backend.matchevent;

import de.dtfb.sportshub.backend.match.Match;
import de.dtfb.sportshub.backend.team.Team;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
public class MatchEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, updatable = false)
    private UUID uuid;

    @ManyToOne
    @JoinColumn(name = "match_id")
    private Match match;

    @ManyToOne
    @JoinColumn(name = "team_id")
    private Team team;

    private UUID playerUuid;

    private Instant timestamp;

    @Enumerated(EnumType.STRING)
    private MatchEventType type;

    private Integer homeScore;
    private Integer awayScore;

    @Column(columnDefinition = "json")
    private String json;
}
