package de.dtfb.sportshub.backend.teamparticipation;

import de.dtfb.sportshub.backend.federation.Federation;
import de.dtfb.sportshub.backend.group.Group;
import de.dtfb.sportshub.backend.group.GroupRepository;
import de.dtfb.sportshub.backend.group.GroupState;
import de.dtfb.sportshub.backend.league.League;
import de.dtfb.sportshub.backend.league.LeagueRepository;
import de.dtfb.sportshub.backend.season.Season;
import de.dtfb.sportshub.backend.season.SeasonNotFoundException;
import de.dtfb.sportshub.backend.season.SeasonRepository;
import de.dtfb.sportshub.backend.tier.Tier;
import de.dtfb.sportshub.backend.tier.TierRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

/**
 * Copy-forward (L1b): seeds a target season from a source season by deep-cloning the league
 * structure (League -> Tier -> Group) and the team placements ({@link TeamParticipation}). Cloned
 * groups reset to {@link GroupState#PLANNED}; last season's fixtures/results (Round/MatchDay/Match/
 * Standing) are NOT carried, and the shared Category / LeagueRuleSet are referenced, not cloned.
 * Each new participation records its {@code copiedFromParticipationId} -- the promotion/relegation
 * audit chain the region admin then edits via the placement CRUD.
 */
@Service
public class CopyForwardService {

    private final SeasonRepository seasonRepository;
    private final LeagueRepository leagueRepository;
    private final TierRepository tierRepository;
    private final GroupRepository groupRepository;
    private final TeamParticipationRepository participationRepository;

    public CopyForwardService(SeasonRepository seasonRepository, LeagueRepository leagueRepository,
                              TierRepository tierRepository, GroupRepository groupRepository,
                              TeamParticipationRepository participationRepository) {
        this.seasonRepository = seasonRepository;
        this.leagueRepository = leagueRepository;
        this.tierRepository = tierRepository;
        this.groupRepository = groupRepository;
        this.participationRepository = participationRepository;
    }

    @Transactional
    public CopyForwardResultDto copyForward(String targetSeasonId, String sourceSeasonId) {
        Season target = seasonRepository.findById(targetSeasonId)
            .orElseThrow(() -> new SeasonNotFoundException(targetSeasonId));
        Season source = seasonRepository.findById(sourceSeasonId)
            .orElseThrow(() -> new SeasonNotFoundException(sourceSeasonId));

        requireSameFederation(source, target);
        requireEmptyTarget(targetSeasonId);

        // old group id -> cloned group, so placements land in the corresponding new division
        Map<String, Group> groupBySourceId = new HashMap<>();
        // old league id -> cloned league, for the participation's league link
        Map<String, League> leagueBySourceId = new HashMap<>();
        int leagues = 0, tiers = 0, groups = 0;

        for (League sourceLeague : leagueRepository.findBySeasonId(sourceSeasonId)) {
            League newLeague = new League();
            newLeague.setSeason(target);
            newLeague.setName(sourceLeague.getName());
            newLeague.setImportId(sourceLeague.getImportId());
            newLeague.setCategory(sourceLeague.getCategory()); // Category is global -- reused, not cloned
            newLeague.setRuleSet(sourceLeague.getRuleSet());    // RuleSet is shared -- referenced, not cloned
            newLeague = leagueRepository.save(newLeague);
            leagueBySourceId.put(sourceLeague.getId(), newLeague);
            leagues++;

            for (Tier sourceTier : tierRepository.findByLeagueId(sourceLeague.getId())) {
                Tier newTier = new Tier();
                newTier.setLeague(newLeague);
                newTier.setName(sourceTier.getName());
                newTier.setRuleSet(sourceTier.getRuleSet()); // shared -- referenced, not cloned
                newTier = tierRepository.save(newTier);
                tiers++;

                for (Group sourceGroup : groupRepository.findByTierId(sourceTier.getId())) {
                    Group newGroup = new Group();
                    newGroup.setTier(newTier);
                    newGroup.setName(sourceGroup.getName());
                    newGroup.setGroupState(GroupState.PLANNED); // a fresh season starts unplayed
                    newGroup = groupRepository.save(newGroup);
                    groupBySourceId.put(sourceGroup.getId(), newGroup);
                    groups++;
                }
            }
        }

        int participations = 0;
        for (TeamParticipation source0 : participationRepository.findByLeague_Season_Id(sourceSeasonId)) {
            League newLeague = source0.getLeague() == null
                ? null : leagueBySourceId.get(source0.getLeague().getId());
            if (newLeague == null) {
                continue; // participation outside the cloned tree; nothing to attach it to
            }
            TeamParticipation clone = new TeamParticipation();
            clone.setTeam(source0.getTeam());
            clone.setLeague(newLeague);
            clone.setGroup(source0.getGroup() == null ? null : groupBySourceId.get(source0.getGroup().getId()));
            clone.setCopiedFromParticipationId(source0.getId());
            participationRepository.save(clone);
            participations++;
        }

        return new CopyForwardResultDto(leagues, tiers, groups, participations);
    }

    private void requireSameFederation(Season source, Season target) {
        Federation sourceRegion = source.getFederation();
        Federation targetRegion = target.getFederation();
        boolean sameRegion = sourceRegion != null && targetRegion != null
            && sourceRegion.getId().equals(targetRegion.getId());
        if (!sameRegion) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Source and target seasons must belong to the same federation");
        }
    }

    private void requireEmptyTarget(String targetSeasonId) {
        if (participationRepository.existsByLeague_Season_Id(targetSeasonId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Target season already has participations; copy-forward only seeds an empty season");
        }
    }
}
