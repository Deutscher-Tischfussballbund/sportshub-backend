package de.dtfb.sportshub.backend.federation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FederationRepository extends JpaRepository<Federation, String> {
    Optional<Federation> findByName(String organisation);

    /** Whether any federation still uses this rule set as its default (rule-set delete guard). */
    boolean existsByDefaultRuleSetId(String ruleSetId);

    /**
     * Every root-level federation (no parent) -- normally exactly one. Returns a list rather than
     * an {@code Optional} so an unexpected extra row doesn't blow up the lookup with a hard
     * {@code IncorrectResultSizeDataAccessException}; callers take the first one.
     */
    List<Federation> findByParentFederationIsNull();

    /** Direct children of a federation. */
    List<Federation> findByParentFederation_Id(String parentId);
}
