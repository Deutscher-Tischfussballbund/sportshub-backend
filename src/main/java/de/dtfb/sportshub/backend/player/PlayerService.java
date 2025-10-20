package de.dtfb.sportshub.backend.player;

import de.dtfb.sportshub.backend.team.Team;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class PlayerService {
    private final PlayerRepository playerRepository;

    public PlayerService(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    public List<Player> getAllPlayers() {
        return playerRepository.findAll();
    }

    public List<Player> getPlayersByTeam(Long teamId) {
        return playerRepository.findByTeamId(teamId);
    }

    public Player getPlayerById(Long id) {
        return playerRepository.findById(id).orElse(null);
    }

    public Player createPlayer(String firstName) {
        log.info("Creating player {}", firstName);
        Player player = new Player();
        Team team = new Team();
        List<Team> teams = new ArrayList<>();
        teams.add(team);
        player.setFirstName(firstName);
        player.setTeam(teams);
        log.info("saving player {}", player);
        return playerRepository.save(player);
    }

    public Player createPlayer(Player player) {
        // TODO technically, the event should be an EventDTO
        //  and instead of id it should have a uuid.
        //  this is a security critical pattern.
        //  for example purpose, this step is omitted
        return playerRepository.save(player);
    }
}
