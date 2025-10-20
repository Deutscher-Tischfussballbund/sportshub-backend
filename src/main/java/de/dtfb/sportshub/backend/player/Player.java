package de.dtfb.sportshub.backend.player;

import de.dtfb.sportshub.backend.team.Team;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Entity
@Getter
@Setter
public class Player {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String firstName;
    private String lastName;

    @ManyToMany(cascade = CascadeType.PERSIST)
    private List<Team> team;

}
