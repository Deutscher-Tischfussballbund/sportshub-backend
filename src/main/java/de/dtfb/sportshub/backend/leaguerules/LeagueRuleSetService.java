package de.dtfb.sportshub.backend.leaguerules;

import de.dtfb.sportshub.backend.federation.Federation;
import de.dtfb.sportshub.backend.federation.FederationNotFoundException;
import de.dtfb.sportshub.backend.federation.FederationRepository;
import de.dtfb.sportshub.backend.league.League;
import de.dtfb.sportshub.backend.league.LeagueNotFoundException;
import de.dtfb.sportshub.backend.league.LeagueRepository;
import de.dtfb.sportshub.backend.teamparticipation.TeamParticipation;
import de.dtfb.sportshub.backend.teamparticipation.TeamParticipationNotFoundException;
import de.dtfb.sportshub.backend.teamparticipation.TeamParticipationRepository;
import de.dtfb.sportshub.backend.tier.Tier;
import de.dtfb.sportshub.backend.tier.TierNotFoundException;
import de.dtfb.sportshub.backend.tier.TierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Rule sets in their two roles (docs/21-rule-set-blueprints.md): the federation's blueprint library
 * (listed, freely editable) and the per-league/tier snapshots (reachable by id, editable until their
 * season ends). Snapshots are created and deleted with their owner, never through this API.
 */
@Service
public class LeagueRuleSetService {
    private final LeagueRuleSetRepository repository;
    private final GamePlanEntryRepository gamePlanRepository;
    private final LeagueRuleSetMapper mapper;
    private final FederationRepository federationRepository;
    private final LeagueRepository leagueRepository;
    private final TierRepository tierRepository;
    private final RuleSetSnapshotService snapshots;
    private final TeamParticipationRepository participationRepository;
    private final LeagueRuleResolver resolver;

    public LeagueRuleSetService(LeagueRuleSetRepository repository,
                                GamePlanEntryRepository gamePlanRepository,
                                LeagueRuleSetMapper mapper,
                                FederationRepository federationRepository,
                                LeagueRepository leagueRepository,
                                TierRepository tierRepository,
                                RuleSetSnapshotService snapshots,
                                TeamParticipationRepository participationRepository,
                                LeagueRuleResolver resolver) {
        this.repository = repository;
        this.gamePlanRepository = gamePlanRepository;
        this.mapper = mapper;
        this.federationRepository = federationRepository;
        this.leagueRepository = leagueRepository;
        this.tierRepository = tierRepository;
        this.snapshots = snapshots;
        this.participationRepository = participationRepository;
        this.resolver = resolver;
    }

    /** The blueprint library (archived ones included, flagged). Snapshots are private to their owner. */
    @Transactional(readOnly = true)
    public List<LeagueRuleSetDto> getAll() {
        return repository.findBySnapshotFalse().stream().map(this::assemble).toList();
    }

    @Transactional(readOnly = true)
    public LeagueRuleSetDto get(String id) {
        LeagueRuleSet ruleSet = repository.findById(id).orElseThrow(
            () -> new LeagueRuleSetNotFoundException(id));
        return assemble(ruleSet);
    }

    /**
     * The rules that actually apply to a team's participation (docs/21): its tier's own rules if the
     * team is placed in a tier that overrides them, otherwise the league's own rules. {@code null}
     * only in an unseeded environment without any rules at all.
     */
    @Transactional(readOnly = true)
    public LeagueRuleSetDto getEffectiveForParticipation(String participationId) {
        TeamParticipation participation = participationRepository.findVisibleById(participationId)
            .orElseThrow(() -> new TeamParticipationNotFoundException(participationId));
        LeagueRuleSet rules = resolver.effectiveFor(participation);
        return rules == null ? null : assemble(rules);
    }

    /** Creates a blueprint. */
    @Transactional
    public LeagueRuleSetDto create(LeagueRuleSetDto dto) {
        LeagueRuleSet ruleSet = mapper.toEntity(dto);
        ruleSet.setSnapshot(false);
        ruleSet.setArchived(Boolean.TRUE.equals(dto.getArchived()));
        ruleSet.setFederation(resolveFederation(dto.getFederationId()));
        LeagueRuleSet saved = repository.save(ruleSet);
        replaceGamePlan(saved, dto.getGamePlan());
        return assemble(saved);
    }

    /**
     * Full-replace update. A blueprint is always editable -- no league references it at runtime, so
     * nothing already played can change. A snapshot is editable while its season runs; once the season
     * has ended, rule-affecting changes are refused ({@code 409 RULE_SET_FROZEN}) and only renaming is
     * allowed. A snapshot's owning federation never changes.
     */
    @Transactional
    public LeagueRuleSetDto update(String id, LeagueRuleSetDto dto) {
        LeagueRuleSet ruleSet = repository.findById(id).orElseThrow(
            () -> new LeagueRuleSetNotFoundException(id));
        boolean changesRules = changesRuleAffectingFields(ruleSet, dto);
        if (ruleSet.isSnapshot() && changesRules) {
            snapshots.requireNotFrozen(ruleSet);
        }
        mapper.updateEntityFromDto(dto, ruleSet);
        if (!ruleSet.isSnapshot()) {
            ruleSet.setFederation(resolveFederation(dto.getFederationId()));
            if (dto.getArchived() != null) {
                ruleSet.setArchived(dto.getArchived());
            }
        }
        LeagueRuleSet saved = repository.save(ruleSet);
        if (changesRules) {
            replaceGamePlan(saved, dto.getGamePlan());
        }
        return assemble(saved);
    }

    /**
     * Deletes a blueprint. Refused while it is a federation's default; snapshots copied from it keep
     * their rules and just lose the lineage link. A snapshot is deleted with its league/tier, never
     * directly.
     */
    @Transactional
    public void delete(String id) {
        LeagueRuleSet ruleSet = repository.findById(id).orElseThrow(
            () -> new LeagueRuleSetNotFoundException(id));
        if (ruleSet.isSnapshot()) {
            throw new RuleSetDeletionBlockedException(
                "This is the rule set of a league or tier; it is deleted together with it");
        }
        if (leagueRepository.existsByRuleSetId(id) || tierRepository.existsByRuleSetId(id)
            || federationRepository.existsByDefaultRuleSetId(id)) {
            throw new RuleSetDeletionBlockedException(
                "Rule set is still a federation's default; choose another default first");
        }
        for (LeagueRuleSet copy : repository.findBySourceBlueprintId(id)) {
            copy.setSourceBlueprint(null);
            repository.save(copy);
        }
        gamePlanRepository.deleteByRuleSetId(id);
        repository.delete(ruleSet);
    }

    /**
     * Copies a rule set (scalar fields + game plan) into a new blueprint -- from a blueprint to start a
     * variant, or from a league's snapshot to keep its tuned rules for reuse.
     */
    @Transactional
    public LeagueRuleSetDto clone(String id) {
        LeagueRuleSet source = repository.findById(id).orElseThrow(
            () -> new LeagueRuleSetNotFoundException(id));
        LeagueRuleSetDto sourceDto = assemble(source);
        LeagueRuleSet copy = mapper.toEntity(sourceDto);
        copy.setSnapshot(false);
        copy.setFederation(source.getFederation());
        copy.setName(source.getName() + " (Kopie)");
        LeagueRuleSet saved = repository.save(copy);
        replaceGamePlan(saved, sourceDto.getGamePlan());
        return assemble(saved);
    }

    /**
     * Overwrites the snapshots of the given leagues/tiers with a blueprint's rules, e.g. after the
     * federation changed its rules for everyone. A listed tier without an override gets one. Refused
     * ({@code 409 RULE_SET_FROZEN}) if any owner's season has ended -- nothing is changed then.
     */
    @Transactional
    public void apply(String blueprintId, ApplyBlueprintRequest request) {
        LeagueRuleSet blueprint = snapshots.requireBlueprint(blueprintId);
        List<League> leagues = (request.getLeagueIds() == null ? List.<String>of() : request.getLeagueIds())
            .stream()
            .map(leagueId -> leagueRepository.findById(leagueId)
                .orElseThrow(() -> new LeagueNotFoundException(leagueId)))
            .toList();
        List<Tier> tiers = (request.getTierIds() == null ? List.<String>of() : request.getTierIds())
            .stream()
            .map(tierId -> tierRepository.findById(tierId).orElseThrow(() -> new TierNotFoundException(tierId)))
            .toList();
        leagues.forEach(league -> snapshots.requireSeasonRunning(league.getSeason()));
        tiers.forEach(tier -> snapshots.requireSeasonRunning(tier.getLeague().getSeason()));

        for (League league : leagues) {
            if (league.getRuleSet() == null) {
                league.setRuleSet(snapshots.snapshotOf(blueprint, league.getSeason().getFederation()));
                leagueRepository.save(league);
            } else {
                snapshots.overwrite(league.getRuleSet(), blueprint);
            }
        }
        for (Tier tier : tiers) {
            if (tier.getRuleSet() == null) {
                tier.setRuleSet(snapshots.snapshotOf(blueprint, tier.getLeague().getSeason().getFederation()));
                tierRepository.save(tier);
            } else {
                snapshots.overwrite(tier.getRuleSet(), blueprint);
            }
        }
    }

    private boolean changesRuleAffectingFields(LeagueRuleSet current, LeagueRuleSetDto dto) {
        return !Objects.equals(current.getPlaySystem(), dto.getPlaySystem())
            || !Objects.equals(current.getPointsWin(), dto.getPointsWin())
            || !Objects.equals(current.getPointsDraw(), dto.getPointsDraw())
            || !Objects.equals(current.getPointsLoss(), dto.getPointsLoss())
            || !Objects.equals(current.getSetsPerGame(), dto.getSetsPerGame())
            || !Objects.equals(current.getPointsToWinSet(), dto.getPointsToWinSet())
            || !Objects.equals(current.getMatchdayDecision(), dto.getMatchdayDecision())
            || !Objects.equals(current.getMatchdayTarget(), dto.getMatchdayTarget())
            || !Objects.equals(current.getSideSwitchAllowed(), dto.getSideSwitchAllowed())
            || !Objects.equals(current.getMinRosterSize(), dto.getMinRosterSize())
            || !Objects.equals(current.getMaxRosterSize(), dto.getMaxRosterSize())
            || !Objects.equals(current.getSchedulingMode(), dto.getSchedulingMode())
            || !Objects.equals(current.getSchedulingWindowDays(), dto.getSchedulingWindowDays())
            || changesGamePlan(current.getId(), dto.getGamePlan());
    }

    private boolean changesGamePlan(String ruleSetId, List<GamePlanEntryDto> requested) {
        List<String> currentSignature = gamePlanRepository.findByRuleSetIdOrderByPositionAsc(ruleSetId).stream()
            .map(e -> e.getPosition() + ":" + e.getGameType())
            .toList();
        List<String> requestedSignature = (requested == null ? List.<GamePlanEntryDto>of() : requested).stream()
            .map(e -> e.getPosition() + ":" + e.getGameType())
            .toList();
        return !currentSignature.equals(requestedSignature);
    }

    private Federation resolveFederation(String federationId) {
        if (federationId == null) {
            return null; // DTFB-global template
        }
        return federationRepository.findById(federationId)
            .orElseThrow(() -> new FederationNotFoundException(federationId));
    }

    private void replaceGamePlan(LeagueRuleSet ruleSet, List<GamePlanEntryDto> gamePlan) {
        gamePlanRepository.deleteByRuleSetId(ruleSet.getId());
        if (gamePlan == null) {
            return;
        }
        for (GamePlanEntryDto entryDto : gamePlan) {
            GamePlanEntry entry = new GamePlanEntry();
            entry.setRuleSet(ruleSet);
            entry.setPosition(entryDto.getPosition());
            entry.setGameType(entryDto.getGameType());
            gamePlanRepository.save(entry);
        }
    }

    private LeagueRuleSetDto assemble(LeagueRuleSet ruleSet) {
        LeagueRuleSetDto dto = mapper.toDto(ruleSet);
        dto.setGamePlan(mapper.toGamePlanDtoList(
            gamePlanRepository.findByRuleSetIdOrderByPositionAsc(ruleSet.getId())));
        dto.setFrozen(snapshots.isFrozen(ruleSet));
        return dto;
    }
}
