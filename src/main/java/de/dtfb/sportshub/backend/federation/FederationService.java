package de.dtfb.sportshub.backend.federation;

import de.dtfb.sportshub.backend.leaguerules.LeagueRuleSet;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleSetNotFoundException;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleSetRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class FederationService {
    private final FederationRepository repository;
    private final FederationMapper mapper;
    private final LeagueRuleSetRepository ruleSetRepository;

    public FederationService(FederationRepository repository,
                             FederationMapper mapper,
                             LeagueRuleSetRepository ruleSetRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.ruleSetRepository = ruleSetRepository;
    }

    @Transactional(readOnly = true)
    public List<FederationDto> getAll() {
        return mapper.toDtoList(repository.findAll());
    }

    @Transactional(readOnly = true)
    public FederationDto get(String id) {
        Federation federation = repository.findById(id).orElseThrow(
            () -> new FederationNotFoundException(id));
        return mapper.toDto(federation);
    }

    @Transactional
    public FederationDto create(FederationDto federationDto) {
        Federation federation = mapper.toEntity(federationDto);
        federation.setDefaultRuleSet(resolveRuleSet(federationDto.getDefaultRuleSetId()));
        federation.setParentFederation(resolveParentForCreate(federationDto.getParentFederationId()));

        Federation savedFederation = repository.save(federation);
        return mapper.toDto(savedFederation);
    }

    /**
     * {@code actingAsAdmin} is the caller's global-admin status (resolved by the controller via
     * {@code authz.isAdmin()}, same pattern as {@code RosterController}'s {@code actingAsAdmin}) --
     * the endpoint itself is open to a region admin managing their own federation (e.g. picking its
     * default rule set, from the rule-set dialog), but re-parenting the federation elsewhere in the
     * tree is a structural move only a global admin may make.
     */
    @Transactional
    public FederationDto update(String id, FederationDto federationDto, boolean actingAsAdmin) {
        Federation federation = repository.findById(id).orElseThrow(
            () -> new FederationNotFoundException(id));

        // The default blueprint is only read when a league is created (docs/21), so changing it
        // never touches an existing league -- no running-league guard needed any more.
        String newRuleSetId = federationDto.getDefaultRuleSetId();

        // A null parentFederationId means "unchanged" (most callers save the whole object back
        // without touching this field) -- re-parenting/re-rooting a federation is an explicit,
        // deliberate move, not something an omitted field should trigger.
        String newParentFederationId = federationDto.getParentFederationId();
        String currentParentFederationId = federation.getParentFederation() == null
            ? null : federation.getParentFederation().getId();
        if (newParentFederationId != null && !newParentFederationId.equals(currentParentFederationId)) {
            if (!actingAsAdmin) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only a global admin may change a federation's parent");
            }
            Federation newParent = repository.findById(newParentFederationId)
                .orElseThrow(() -> new FederationNotFoundException(newParentFederationId));
            requireNoCycle(federation, newParent);
            federation.setParentFederation(newParent);
        }

        mapper.updateEntityFromDto(federationDto, federation);
        federation.setDefaultRuleSet(resolveRuleSet(newRuleSetId));

        Federation savedFederation = repository.save(federation);
        return mapper.toDto(savedFederation);
    }

    /** The default must be a blueprint -- a league's private snapshot can't serve as a template. */
    private LeagueRuleSet resolveRuleSet(String ruleSetId) {
        if (ruleSetId == null) {
            return null;
        }
        LeagueRuleSet ruleSet = ruleSetRepository.findById(ruleSetId)
            .orElseThrow(() -> new LeagueRuleSetNotFoundException(ruleSetId));
        if (ruleSet.isSnapshot()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "The default rule set must be a blueprint, not a league's own rules");
        }
        return ruleSet;
    }

    /**
     * A new federation with no explicit parent attaches under the existing root, if any -- callers
     * shouldn't need to know/pass the root's id just to create an ordinary sub-federation. The very
     * first federation ever created (no root exists yet) becomes the root itself. This keeps the
     * tree singly-rooted without a separate "no other root" guard.
     */
    private Federation resolveParentForCreate(String parentFederationId) {
        if (parentFederationId != null) {
            return repository.findById(parentFederationId)
                .orElseThrow(() -> new FederationNotFoundException(parentFederationId));
        }
        List<Federation> roots = repository.findByParentFederationIsNull();
        return roots.isEmpty() ? null : roots.get(0);
    }

    /** A federation can't become its own ancestor by reassigning its parent. */
    private void requireNoCycle(Federation federation, Federation newParent) {
        for (Federation current = newParent; current != null; current = current.getParentFederation()) {
            if (current.getId().equals(federation.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A federation cannot be its own ancestor");
            }
        }
    }

    @Transactional
    public void delete(String id) {
        Federation federation = repository.findById(id).orElseThrow(
            () -> new FederationNotFoundException(id));
        repository.delete(federation);
    }
}
