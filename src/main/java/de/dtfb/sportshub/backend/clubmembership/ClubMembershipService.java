package de.dtfb.sportshub.backend.clubmembership;

import de.dtfb.sportshub.backend.club.Club;
import de.dtfb.sportshub.backend.club.ClubDto;
import de.dtfb.sportshub.backend.club.ClubMapper;
import de.dtfb.sportshub.backend.club.ClubNotFoundException;
import de.dtfb.sportshub.backend.club.ClubRepository;
import de.dtfb.sportshub.backend.player.Player;
import de.dtfb.sportshub.backend.player.PlayerNotFoundException;
import de.dtfb.sportshub.backend.player.PlayerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A player's club membership(s) -- the precondition for being rostered onto one of a club's teams.
 * Independent of team rosters: a player joins a club here first, then can be added to that club's
 * team roster (enforced by {@link de.dtfb.sportshub.backend.roster.RosterService#addPlayer}).
 */
@Service
public class ClubMembershipService {

    private final ClubMembershipRepository repository;
    private final PlayerRepository playerRepository;
    private final ClubRepository clubRepository;
    private final ClubMapper clubMapper;

    public ClubMembershipService(ClubMembershipRepository repository, PlayerRepository playerRepository,
                                 ClubRepository clubRepository, ClubMapper clubMapper) {
        this.repository = repository;
        this.playerRepository = playerRepository;
        this.clubRepository = clubRepository;
        this.clubMapper = clubMapper;
    }

    /** Idempotent: a no-op if the player is already an active member of the club. */
    @Transactional
    public void join(String playerId, String clubId) {
        if (repository.existsByPlayerIdAndClubIdAndLeftAtIsNull(playerId, clubId)) {
            return;
        }
        Player player = playerRepository.findById(playerId).orElseThrow(() -> new PlayerNotFoundException(playerId));
        Club club = clubRepository.findById(clubId).orElseThrow(() -> new ClubNotFoundException(clubId));

        ClubMembership membership = new ClubMembership();
        membership.setPlayer(player);
        membership.setClub(club);
        membership.setJoinedAt(Instant.now());
        repository.save(membership);
    }

    @Transactional
    public void leave(String playerId, String clubId) {
        ClubMembership membership = repository.findByPlayerIdAndClubIdAndLeftAtIsNull(playerId, clubId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Player is not an active member of this club"));
        membership.setLeftAt(Instant.now());
        repository.save(membership);
    }

    @Transactional(readOnly = true)
    public boolean isActiveMember(String playerId, String clubId) {
        return repository.existsByPlayerIdAndClubIdAndLeftAtIsNull(playerId, clubId);
    }

    /** Every player's active club memberships, in one query -- keyed by player id, deduped by club id. */
    @Transactional(readOnly = true)
    public Map<String, List<ClubDto>> clubsByPlayerId(Collection<String> playerIds) {
        Map<String, Map<String, ClubDto>> clubsByPlayer = new LinkedHashMap<>();
        for (ClubMembership membership : repository.findByPlayerIdInAndLeftAtIsNull(playerIds)) {
            String playerId = membership.getPlayer().getId();
            Club club = membership.getClub();
            clubsByPlayer
                .computeIfAbsent(playerId, id -> new LinkedHashMap<>())
                .putIfAbsent(club.getId(), clubMapper.toDto(club));
        }
        Map<String, List<ClubDto>> result = new LinkedHashMap<>();
        clubsByPlayer.forEach((playerId, clubs) -> result.put(playerId, List.copyOf(clubs.values())));
        return result;
    }
}
