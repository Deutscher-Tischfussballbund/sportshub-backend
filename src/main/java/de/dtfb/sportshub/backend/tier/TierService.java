package de.dtfb.sportshub.backend.tier;

import de.dtfb.sportshub.backend.league.League;
import de.dtfb.sportshub.backend.league.LeagueNotFoundException;
import de.dtfb.sportshub.backend.league.LeagueRepository;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleSet;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleSetRepository;
import de.dtfb.sportshub.backend.leaguerules.RuleSetSnapshotService;
import de.dtfb.sportshub.backend.group.GroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class TierService {
    private final TierRepository repository;
    private final TierMapper mapper;
    private final LeagueRepository leagueRepository;
    private final LeagueRuleSetRepository ruleSetRepository;
    private final GroupRepository groupRepository;
    private final RuleSetSnapshotService snapshots;

    public TierService(TierRepository repository,
                       TierMapper mapper,
                       LeagueRepository leagueRepository,
                       LeagueRuleSetRepository ruleSetRepository,
                       GroupRepository groupRepository,
                       RuleSetSnapshotService snapshots) {
        this.repository = repository;
        this.mapper = mapper;
        this.leagueRepository = leagueRepository;
        this.ruleSetRepository = ruleSetRepository;
        this.groupRepository = groupRepository;
        this.snapshots = snapshots;
    }

    @Transactional(readOnly = true)
    public List<TierDto> getAll() {
        return mapper.toDtoList(repository.findAllVisible());
    }

    @Transactional(readOnly = true)
    public TierDto get(String id) {
        Tier tier = repository.findVisibleById(id).orElseThrow(
            () -> new TierNotFoundException(id));
        return mapper.toDto(tier);
    }

    /** Creates the tier; a chosen blueprint gives it its own rules override (docs/21). */
    @Transactional
    public TierDto create(TierDto tierDto) {
        Tier tier = mapper.toEntity(tierDto);
        tier.setLeague(resolveLeague(tierDto.getLeagueId()));
        LeagueRuleSet blueprint = chosenBlueprint(tierDto);
        if (blueprint != null) {
            tier.setRuleSet(snapshots.snapshotOf(blueprint, tier.getLeague().getSeason().getFederation()));
        }
        return mapper.toDto(repository.save(tier));
    }

    /**
     * Updates the tier's meta and its rules override: a new/different blueprint creates or resets the
     * override; sending neither a blueprint nor the current override id removes it (the league's rules
     * apply again). Any change to the override is refused once the season has ended.
     */
    @Transactional
    public TierDto update(String id, TierDto tierDto) {
        Tier tier = repository.findById(id).orElseThrow(
            () -> new TierNotFoundException(id));
        mapper.updateEntityFromDto(tierDto, tier);
        tier.setLeague(resolveLeague(tierDto.getLeagueId()));

        LeagueRuleSet current = tier.getRuleSet();
        LeagueRuleSet blueprint = chosenBlueprint(tierDto);
        LeagueRuleSet removed = null;
        if (blueprint != null) {
            if (current == null) {
                snapshots.requireSeasonRunning(tier.getLeague().getSeason());
                tier.setRuleSet(snapshots.snapshotOf(blueprint, tier.getLeague().getSeason().getFederation()));
            } else if (!isFrom(current, blueprint)) {
                snapshots.requireSeasonRunning(tier.getLeague().getSeason());
                snapshots.overwrite(current, blueprint);
            }
        } else if (current != null && tierDto.getRuleSetId() == null) {
            snapshots.requireSeasonRunning(tier.getLeague().getSeason());
            tier.setRuleSet(null);
            removed = current;
        }
        TierDto saved = mapper.toDto(repository.save(tier));
        if (removed != null) {
            repository.flush();
            snapshots.delete(removed);
        }
        return saved;
    }

    /** A tier blocks its own delete while it still has a {@code Group} underneath it. Its override goes with it. */
    @Transactional
    public void delete(String id) {
        Tier tier = repository.findById(id).orElseThrow(
            () -> new TierNotFoundException(id));
        if (groupRepository.existsByTierId(id)) {
            throw new TierDeletionBlockedException("Tier has groups; remove them before deleting the tier");
        }
        LeagueRuleSet ruleSet = tier.getRuleSet();
        repository.delete(tier);
        repository.flush();
        snapshots.delete(ruleSet);
    }

    private League resolveLeague(String leagueId) {
        return leagueRepository.findById(leagueId)
            .orElseThrow(() -> new LeagueNotFoundException(leagueId));
    }

    /** As in {@code LeagueService}: {@code blueprintId}, or a {@code ruleSetId} naming a blueprint (older clients). */
    private LeagueRuleSet chosenBlueprint(TierDto dto) {
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
