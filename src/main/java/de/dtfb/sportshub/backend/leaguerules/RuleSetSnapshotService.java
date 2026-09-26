package de.dtfb.sportshub.backend.leaguerules;

import de.dtfb.sportshub.backend.federation.Federation;
import de.dtfb.sportshub.backend.league.League;
import de.dtfb.sportshub.backend.league.LeagueRepository;
import de.dtfb.sportshub.backend.season.Season;
import de.dtfb.sportshub.backend.tier.Tier;
import de.dtfb.sportshub.backend.tier.TierRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Blueprint → snapshot mechanics (docs/21-rule-set-blueprints.md): every {@link League} owns a
 * private snapshot of its rules, a {@link Tier} optionally one of its own as an override. Snapshots
 * are created from a blueprint (or copied from last season's snapshot by copy-forward), are editable
 * while their season runs, and freeze once it has ended ({@link Season#hasEnded()}).
 */
@Service
public class RuleSetSnapshotService {

    /** Well-known id of the seeded DTFB-global blueprint -- the last default for a new league. */
    static final String DTFB_STANDARD_ID = LeagueRuleResolver.DTFB_STANDARD_ID;

    private final LeagueRuleSetRepository repository;
    private final GamePlanEntryRepository gamePlanRepository;
    private final LeagueRuleSetMapper mapper;
    private final LeagueRepository leagueRepository;
    private final TierRepository tierRepository;

    public RuleSetSnapshotService(LeagueRuleSetRepository repository,
                                  GamePlanEntryRepository gamePlanRepository,
                                  LeagueRuleSetMapper mapper,
                                  LeagueRepository leagueRepository,
                                  TierRepository tierRepository) {
        this.repository = repository;
        this.gamePlanRepository = gamePlanRepository;
        this.mapper = mapper;
        this.leagueRepository = leagueRepository;
        this.tierRepository = tierRepository;
    }

    /**
     * The blueprint to use for a new league when none was chosen: the federation's default, else the
     * seeded DTFB-global blueprint, else {@code null} (a blank snapshot then falls back to the
     * resolver's historical defaults).
     */
    @Transactional(readOnly = true)
    public LeagueRuleSet defaultBlueprintFor(Federation federation) {
        LeagueRuleSet federationDefault = federation == null ? null : federation.getDefaultRuleSet();
        if (federationDefault != null && !federationDefault.isSnapshot()) {
            return federationDefault;
        }
        return repository.findById(DTFB_STANDARD_ID).filter(r -> !r.isSnapshot()).orElse(null);
    }

    /** Looks up a blueprint by id; 400 if the id names a snapshot (only blueprints can be chosen). */
    @Transactional(readOnly = true)
    public LeagueRuleSet requireBlueprint(String blueprintId) {
        LeagueRuleSet blueprint = repository.findById(blueprintId)
            .orElseThrow(() -> new LeagueRuleSetNotFoundException(blueprintId));
        if (blueprint.isSnapshot()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Rule set " + blueprintId + " is the private rule set of a league, not a blueprint");
        }
        return blueprint;
    }

    /**
     * A new snapshot copied from {@code source} (a blueprint, or another snapshot as in copy-forward),
     * owned by {@code federation}. The lineage points at the original blueprint either way.
     * {@code source == null} yields a blank snapshot.
     */
    @Transactional
    public LeagueRuleSet snapshotOf(LeagueRuleSet source, Federation federation) {
        LeagueRuleSet snapshot = new LeagueRuleSet();
        snapshot.setSnapshot(true);
        snapshot.setFederation(federation);
        if (source != null) {
            mapper.copyRules(source, snapshot);
            snapshot.setSourceBlueprint(source.isSnapshot() ? source.getSourceBlueprint() : source);
        }
        LeagueRuleSet saved = repository.save(snapshot);
        if (source != null) {
            copyGamePlan(source, saved);
        }
        return saved;
    }

    /** Overwrites an existing snapshot's rules with a blueprint's (reset / "apply blueprint"). */
    @Transactional
    public void overwrite(LeagueRuleSet snapshot, LeagueRuleSet blueprint) {
        requireNotFrozen(snapshot);
        mapper.copyRules(blueprint, snapshot);
        snapshot.setSourceBlueprint(blueprint);
        repository.save(snapshot);
        gamePlanRepository.deleteByRuleSetId(snapshot.getId());
        copyGamePlan(blueprint, snapshot);
    }

    /** Deletes a snapshot (and its game plan) once its owner no longer references it. No-op for blueprints. */
    @Transactional
    public void delete(LeagueRuleSet snapshot) {
        if (snapshot == null || !snapshot.isSnapshot()) {
            return;
        }
        gamePlanRepository.deleteByRuleSetId(snapshot.getId());
        repository.delete(snapshot);
    }

    /** The season whose league/tier owns this snapshot, or {@code null} for a blueprint / an orphan. */
    @Transactional(readOnly = true)
    public Season ownerSeason(LeagueRuleSet ruleSet) {
        if (ruleSet == null || !ruleSet.isSnapshot()) {
            return null;
        }
        League league = leagueRepository.findFirstByRuleSetId(ruleSet.getId())
            .orElseGet(() -> tierRepository.findFirstByRuleSetId(ruleSet.getId())
                .map(Tier::getLeague).orElse(null));
        return league == null ? null : league.getSeason();
    }

    /** Whether the snapshot's season has ended, so its rules must no longer change. */
    @Transactional(readOnly = true)
    public boolean isFrozen(LeagueRuleSet ruleSet) {
        Season season = ownerSeason(ruleSet);
        return season != null && season.hasEnded();
    }

    /** Refuses (409 {@code RULE_SET_FROZEN}) any change to the rules of an ended season. */
    public void requireNotFrozen(LeagueRuleSet ruleSet) {
        if (isFrozen(ruleSet)) {
            throw new RuleSetEditBlockedException(
                "The season has ended, so its rules are frozen and can no longer be changed");
        }
    }

    /** Refuses a change that would alter the effective rules of an ended season (e.g. a tier override). */
    public void requireSeasonRunning(Season season) {
        if (season != null && season.hasEnded()) {
            throw new RuleSetEditBlockedException(
                "The season has ended, so its rules are frozen and can no longer be changed");
        }
    }

    /**
     * Brings older data onto the blueprint model (docs/21): every league without rules gets a
     * snapshot of its federation's default, and every league/tier still pointing at a shared rule set
     * gets its own snapshot of it -- so the shared rows are left as plain blueprints. Idempotent: a
     * second run finds nothing to do. Returns the number of snapshots created.
     */
    @Transactional
    public int backfill() {
        int created = 0;
        for (League league : leagueRepository.findAll()) {
            LeagueRuleSet current = league.getRuleSet();
            if (current != null && current.isSnapshot()) {
                continue;
            }
            Federation federation = league.getSeason() == null ? null : league.getSeason().getFederation();
            LeagueRuleSet source = current != null ? current : defaultBlueprintFor(federation);
            league.setRuleSet(snapshotOf(source, federation));
            leagueRepository.save(league);
            created++;
        }
        for (Tier tier : tierRepository.findAll()) {
            LeagueRuleSet current = tier.getRuleSet();
            if (current == null || current.isSnapshot()) {
                continue;
            }
            Season season = tier.getLeague() == null ? null : tier.getLeague().getSeason();
            tier.setRuleSet(snapshotOf(current, season == null ? null : season.getFederation()));
            tierRepository.save(tier);
            created++;
        }
        return created;
    }

    private void copyGamePlan(LeagueRuleSet from, LeagueRuleSet to) {
        for (GamePlanEntry sourceEntry : gamePlanRepository.findByRuleSetIdOrderByPositionAsc(from.getId())) {
            GamePlanEntry entry = new GamePlanEntry();
            entry.setRuleSet(to);
            entry.setPosition(sourceEntry.getPosition());
            entry.setGameType(sourceEntry.getGameType());
            gamePlanRepository.save(entry);
        }
    }
}
