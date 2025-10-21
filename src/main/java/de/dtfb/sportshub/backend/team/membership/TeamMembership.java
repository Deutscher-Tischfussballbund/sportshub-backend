package de.dtfb.sportshub.backend.team.membership;

import de.dtfb.sportshub.backend.team.Team;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class TeamMembership {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "team_id")
    private Team team;

    private Long playerId; // TODO coming from external system (e.g. dtfb api)

    @Enumerated(EnumType.STRING)
    private TeamMembershipEnum role;

}
