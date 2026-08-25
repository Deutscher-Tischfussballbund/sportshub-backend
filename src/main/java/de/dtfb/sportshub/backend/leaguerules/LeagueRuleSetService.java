package de.dtfb.sportshub.backend.leaguerules;

import de.dtfb.sportshub.backend.federation.Federation;
import de.dtfb.sportshub.backend.federation.FederationNotFoundException;
import de.dtfb.sportshub.backend.federation.FederationRepository;
import de.dtfb.sportshub.backend.league.LeagueRepository;
import de.dtfb.sportshub.backend.tier.TierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class LeagueRuleSetService {
    private final LeagueRuleSetRepository repository;
    private final GamePlanEntryRepository gamePlanRepository;
    private final LeagueRuleSetMapper mapper;
    private final FederationRepository federationRepository;
    private final LeagueRepository leagueRepository;
    private final TierRepository tierRepository;

    public LeagueRuleSetService(LeagueRuleSetRepository repository,
                                GamePlanEntryRepository gamePlanRepository,
                                LeagueRuleSetMapper mapper,
                                FederationRepository federationRepository,
                                LeagueRepository leagueRepository,
                                TierRepository tierRepository) {
        this.repository = repository;
        this.gamePlanRepository = gamePlanRepository;
        this.mapper = mapper;
        this.federationRepository = federationRepository;
        this.leagueRepository = leagueRepository;
        this.tierRepository = tierRepository;
    }

    @Transactional(readOnly = true)
    public List<LeagueRuleSetDto> getAll() {
        return repository.findAll().stream().map(this::assemble).toList();
    }

    @Transactional(readOnly = true)
    public LeagueRuleSetDto get(String id) {
        LeagueRuleSet ruleSet = repository.findById(id).orElseThrow(
            () -> new LeagueRuleSetNotFoundException(id));
        return assemble(ruleSet);
    }

    @Transactional
    public LeagueRuleSetDto create(LeagueRuleSetDto dto) {
        LeagueRuleSet ruleSet = mapper.toEntity(dto);
        ruleSet.setFederation(resolveFederation(dto.getFederationId()));
        LeagueRuleSet saved = repository.save(ruleSet);
        replaceGamePlan(saved, dto.getGamePlan());
        return assemble(saved);
    }

    /**
     * Full-replace update. If the request actually changes a rule-affecting field (not just
     * re-sending the same values) AND this rule set is already referenced by a League/Tier of a
     * closed (archived) season, the edit is refused -- that would silently rewrite the rules behind
     * a season's finalized results. Renaming (`name`/`federationId`) is exempt: purely cosmetic,
     * doesn't affect any computed result. Clone the rule set (see {@link #clone(String)}) to diverge.
     */
    @Transactional
    public LeagueRuleSetDto update(String id, LeagueRuleSetDto dto) {
        LeagueRuleSet ruleSet = repository.findById(id).orElseThrow(
            () -> new LeagueRuleSetNotFoundException(id));
        if (changesRuleAffectingFields(ruleSet, dto) && isLockedByClosedSeason(id)) {
            throw new RuleSetEditBlockedException(
                "Rule set is already used by a closed season and its rule-affecting fields can no "
                    + "longer be changed -- clone it (POST /v1/league-rule-sets/" + id
                    + "/clone) and edit the copy instead. Renaming is still allowed.");
        }
        mapper.updateEntityFromDto(dto, ruleSet);
        ruleSet.setFederation(resolveFederation(dto.getFederationId()));
        LeagueRuleSet saved = repository.save(ruleSet);
        replaceGamePlan(saved, dto.getGamePlan());
        return assemble(saved);
    }

    /**
     * A rule set blocks its own delete while any League, Tier, or Federation default still
     * references it -- detach those first.
     */
    @Transactional
    public void delete(String id) {
        LeagueRuleSet ruleSet = repository.findById(id).orElseThrow(
            () -> new LeagueRuleSetNotFoundException(id));
        if (leagueRepository.existsByRuleSetId(id) || tierRepository.existsByRuleSetId(id)
            || federationRepository.existsByDefaultRuleSetId(id)) {
            throw new RuleSetDeletionBlockedException(
                "Rule set is still referenced by a league, tier, or federation default; detach it first");
        }
        gamePlanRepository.deleteByRuleSetId(id);
        repository.delete(ruleSet);
    }

    /**
     * Copies a rule set (scalar fields + game plan) into a new, unreferenced row -- immediately
     * editable regardless of the source's lock state, since the guard only ever inspects the ruleset
     * being edited, not its lineage. Gives the blocked-edit error a concrete next action.
     */
    @Transactional
    public LeagueRuleSetDto clone(String id) {
        LeagueRuleSet source = repository.findById(id).orElseThrow(
            () -> new LeagueRuleSetNotFoundException(id));
        LeagueRuleSetDto sourceDto = assemble(source);
        LeagueRuleSet copy = mapper.toEntity(sourceDto);
        copy.setFederation(source.getFederation());
        copy.setName(source.getName() + " (Kopie)");
        LeagueRuleSet saved = repository.save(copy);
        replaceGamePlan(saved, sourceDto.getGamePlan());
        return assemble(saved);
    }

    private boolean isLockedByClosedSeason(String ruleSetId) {
        return leagueRepository.existsByRuleSetIdAndSeason_ArchivedAtIsNotNull(ruleSetId)
            || tierRepository.existsByRuleSetIdAndLeague_Season_ArchivedAtIsNotNull(ruleSetId);
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
        return dto;
    }
}
