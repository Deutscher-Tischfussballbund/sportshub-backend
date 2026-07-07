package de.dtfb.sportshub.backend.leaguerules;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GamePlanEntryRepository extends JpaRepository<GamePlanEntry, String> {
    List<GamePlanEntry> findByRuleSetIdOrderByPositionAsc(String ruleSetId);

    void deleteByRuleSetId(String ruleSetId);
}
