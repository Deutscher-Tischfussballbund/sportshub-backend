package de.dtfb.sportshub.backend.league;

import de.dtfb.sportshub.backend.group.Group;
import de.dtfb.sportshub.backend.group.GroupRepository;
import de.dtfb.sportshub.backend.teamparticipation.TeamParticipation;
import de.dtfb.sportshub.backend.teamparticipation.TeamParticipationRepository;
import de.dtfb.sportshub.backend.tier.Tier;
import de.dtfb.sportshub.backend.tier.TierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Builds the {@link LeagueStructureDto} -- a league's Tier -> Group subtree plus a participation
 * count per group. Kept out of {@link LeagueService} (which owns League CRUD) so each service stays
 * single-purpose. One flat query per level, assembled in memory: the result is bounded to a single
 * league, so the walk is cheap and the query count is constant.
 */
@Service
public class LeagueStructureService {

    private final LeagueRepository leagueRepository;
    private final TierRepository tierRepository;
    private final GroupRepository groupRepository;
    private final TeamParticipationRepository participationRepository;

    public LeagueStructureService(
        LeagueRepository leagueRepository,
        TierRepository tierRepository,
        GroupRepository groupRepository,
        TeamParticipationRepository participationRepository
    ) {
        this.leagueRepository = leagueRepository;
        this.tierRepository = tierRepository;
        this.groupRepository = groupRepository;
        this.participationRepository = participationRepository;
    }

    @Transactional(readOnly = true)
    public LeagueStructureDto get(String leagueId) {
        League league = leagueRepository.findById(leagueId)
            .orElseThrow(() -> new LeagueNotFoundException(leagueId));

        List<Tier> tiers = tierRepository.findByLeagueId(leagueId);
        Map<String, List<Group>> groupsByTier = groupRepository.findByTier_League_Id(leagueId)
            .stream().collect(Collectors.groupingBy(g -> g.getTier().getId()));

        // Placed participations only; an unplaced one (null group) is registered but not in any group.
        Map<String, Long> countByGroup = participationRepository.findVisibleByLeagueId(leagueId).stream()
            .map(TeamParticipation::getGroup)
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(Group::getId, Collectors.counting()));

        List<LeagueStructureDto.TierNode> tierNodes = tiers.stream()
            .map(t -> new LeagueStructureDto.TierNode(
                t.getId(),
                t.getName(),
                groupsByTier.getOrDefault(t.getId(), List.of()).stream()
                    .map(g -> new LeagueStructureDto.GroupNode(
                        g.getId(),
                        g.getName(),
                        g.getGroupState(),
                        countByGroup.getOrDefault(g.getId(), 0L).intValue()
                    ))
                    .toList()
            ))
            .toList();

        return new LeagueStructureDto(league.getId(), league.getName(), tierNodes);
    }
}
