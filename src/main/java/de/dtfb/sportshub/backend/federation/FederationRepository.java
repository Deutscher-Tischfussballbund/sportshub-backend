package de.dtfb.sportshub.backend.federation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FederationRepository extends JpaRepository<Federation, String> {
    Optional<Federation> findByName(String organisation);

    /** Whether any federation still uses this rule set as its default (rule-set delete guard). */
    boolean existsByDefaultRuleSetId(String ruleSetId);
}
