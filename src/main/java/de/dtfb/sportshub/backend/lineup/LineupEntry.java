package de.dtfb.sportshub.backend.lineup;

import de.dtfb.sportshub.backend.base.BaseEntity;
import de.dtfb.sportshub.backend.match.Match;
import de.dtfb.sportshub.backend.player.Player;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** One player lined up for one game (slot 1 or 2 of a double, 1 of a single) -- docs/23. */
@Entity
@Getter
@Setter
public class LineupEntry extends BaseEntity {
    @ManyToOne(optional = false)
    @JoinColumn(name = "lineup_id")
    private Lineup lineup;

    @ManyToOne(optional = false)
    @JoinColumn(name = "match_id")
    private Match match;

    private int slot;

    @ManyToOne(optional = false)
    @JoinColumn(name = "player_id")
    private Player player;
}
