package de.dtfb.sportshub.backend.team;

import de.dtfb.sportshub.backend.player.Player;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Entity
@Getter
@Setter
public class Team {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;

    @ManyToMany(cascade = CascadeType.PERSIST)
    private List<Player> players;
}
