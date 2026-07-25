package de.dtfb.sportshub.backend.round;

import de.dtfb.sportshub.backend.group.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface RoundRepository extends JpaRepository<Round, String> {
    Optional<Round> findByGroupAndName(Group group, String name);

    @Query("select e from Round e where e.group.tier.league.season.archivedAt is null")
    List<Round> findAllVisible();

    @Query("select e from Round e where e.id = :id and e.group.tier.league.season.archivedAt is null")
    Optional<Round> findVisibleById(String id);

    /**
     * Whether the federation has a tier with fixtures (a {@code Round}) that has no explicit rule
     * set anywhere in its own chain (neither the tier nor its league) — i.e. is silently relying on
     * the federation's default. Guards {@code Federation.defaultRuleSetId} changes.
     */
    @Query("select count(r) > 0 from Round r where r.group.tier.league.season.federation.id = :federationId "
        + "and r.group.tier.ruleSet is null and r.group.tier.league.ruleSet is null")
    boolean existsFixtureForFederationDefaultDependentTier(String federationId);
}
