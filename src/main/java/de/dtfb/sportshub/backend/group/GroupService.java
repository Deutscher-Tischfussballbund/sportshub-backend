package de.dtfb.sportshub.backend.group;

import de.dtfb.sportshub.backend.leaguerules.LeagueRuleSet;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleSetRepository;
import de.dtfb.sportshub.backend.leaguerules.RuleSetSnapshotService;
import de.dtfb.sportshub.backend.match.MatchPlanService;
import de.dtfb.sportshub.backend.round.RoundRepository;
import de.dtfb.sportshub.backend.teamparticipation.TeamParticipationRepository;
import de.dtfb.sportshub.backend.tier.Tier;
import de.dtfb.sportshub.backend.tier.TierNotFoundException;
import de.dtfb.sportshub.backend.tier.TierRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class GroupService {
    private final GroupRepository repository;
    private final GroupMapper mapper;
    private final TierRepository tierRepository;
    private final TeamParticipationRepository teamParticipationRepository;
    private final RoundRepository roundRepository;
    private final LeagueRuleSetRepository ruleSetRepository;
    private final RuleSetSnapshotService snapshots;
    private final MatchPlanService matchPlan;

    public GroupService(GroupRepository repository, GroupMapper mapper, TierRepository tierRepository,
                        TeamParticipationRepository teamParticipationRepository, RoundRepository roundRepository,
                        LeagueRuleSetRepository ruleSetRepository, RuleSetSnapshotService snapshots,
                        MatchPlanService matchPlan) {
        this.repository = repository;
        this.mapper = mapper;
        this.tierRepository = tierRepository;
        this.teamParticipationRepository = teamParticipationRepository;
        this.roundRepository = roundRepository;
        this.ruleSetRepository = ruleSetRepository;
        this.snapshots = snapshots;
        this.matchPlan = matchPlan;
    }

    @Transactional(readOnly = true)
    public List<GroupDto> getAll(String tierId, String leagueId, String seasonId) {
        return mapper.toDtoList(repository.findVisible(tierId, leagueId, seasonId));
    }

    @Transactional(readOnly = true)
    public GroupDto get(String id) {
        Group group = repository.findVisibleById(id).orElseThrow(
            () -> new GroupNotFoundException(id));
        return mapper.toDto(group);
    }

    @Transactional
    public GroupDto create(GroupDto groupDto) {
        Group group = mapper.toEntity(groupDto);
        group.setKind(groupDto.getKind() == null ? GroupKind.ROUND_ROBIN : groupDto.getKind());

        Tier tier = tierRepository.findById(groupDto.getTierId())
            .orElseThrow(() -> new TierNotFoundException(groupDto.getTierId()));
        group.setTier(tier);
        LeagueRuleSet blueprint = chosenBlueprint(groupDto);
        if (blueprint != null) {
            group.setRuleSet(snapshots.snapshotOf(blueprint, tier.getLeague().getSeason().getFederation()));
        }

        Group savedGroup = repository.save(group);
        return mapper.toDto(savedGroup);
    }

    /**
     * Updates the group's meta, kind and rules override. The kind is fixed once the group has rounds
     * (409), and a group with placed teams can't become a knock-out stage. The override follows the
     * tier's contract ({@code TierService.update}): a new/different blueprint creates or resets it,
     * sending neither a blueprint nor the current override id removes it; refused once the season has
     * ended, and if it changes the game plan after a result was entered.
     */
    @Transactional
    public GroupDto update(String id, GroupDto groupDto) {
        Group group = repository.findById(id).orElseThrow(
            () -> new GroupNotFoundException(id));

        mapper.updateEntityFromDto(groupDto, group);
        if (groupDto.getKind() != null && groupDto.getKind() != group.getKind()) {
            if (roundRepository.existsByGroupId(id)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The group already has fixtures; its kind can no longer change");
            }
            if (groupDto.getKind() == GroupKind.KNOCKOUT && teamParticipationRepository.existsByGroup_Id(id)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Teams are placed in this group; a knock-out stage takes its teams from other groups");
            }
            group.setKind(groupDto.getKind());
        }

        Tier tier = tierRepository.findById(groupDto.getTierId())
            .orElseThrow(() -> new TierNotFoundException(groupDto.getTierId()));
        group.setTier(tier);

        LeagueRuleSet current = group.getRuleSet();
        LeagueRuleSet blueprint = chosenBlueprint(groupDto);
        List<Group> affected = List.of(group);
        Map<String, List<String>> plansBefore = matchPlan.planSignatures(affected);
        LeagueRuleSet removed = null;
        if (blueprint != null) {
            if (current == null) {
                snapshots.requireSeasonRunning(tier.getLeague().getSeason());
                group.setRuleSet(snapshots.snapshotOf(blueprint, tier.getLeague().getSeason().getFederation()));
            } else if (!isFrom(current, blueprint)) {
                snapshots.requireSeasonRunning(tier.getLeague().getSeason());
                snapshots.overwrite(current, blueprint);
            }
        } else if (current != null && groupDto.getRuleSetId() == null) {
            snapshots.requireSeasonRunning(tier.getLeague().getSeason());
            group.setRuleSet(null);
            removed = current;
        }

        Group savedGroup = repository.save(group);
        matchPlan.afterRuleChange(affected, plansBefore);
        if (removed != null) {
            repository.flush();
            snapshots.delete(removed);
        }
        return mapper.toDto(savedGroup);
    }

    /** A group blocks its own delete while a team is still placed in it. Its override goes with it. */
    @Transactional
    public void delete(String id) {
        Group group = repository.findById(id).orElseThrow(
            () -> new GroupNotFoundException(id));
        if (teamParticipationRepository.existsByGroup_Id(id)) {
            throw new GroupDeletionBlockedException(
                "Group has team participations placed in it; move or unplace them before deleting the group");
        }
        LeagueRuleSet ruleSet = group.getRuleSet();
        repository.delete(group);
        repository.flush();
        snapshots.delete(ruleSet);
    }

    /** As in {@code TierService}: {@code blueprintId}, or a {@code ruleSetId} naming a blueprint. */
    private LeagueRuleSet chosenBlueprint(GroupDto dto) {
        if (dto.getBlueprintId() != null) {
            return snapshots.requireBlueprint(dto.getBlueprintId());
        }
        if (dto.getRuleSetId() != null) {
            return ruleSetRepository.findById(dto.getRuleSetId()).filter(r -> !r.isSnapshot()).orElse(null);
        }
        return null;
    }

    private static boolean isFrom(LeagueRuleSet snapshot, LeagueRuleSet blueprint) {
        return snapshot.getSourceBlueprint() != null
            && Objects.equals(snapshot.getSourceBlueprint().getId(), blueprint.getId());
    }
}
