package de.dtfb.sportshub.backend.leaguerules;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Body of {@code POST /v1/league-rule-sets/{blueprintId}/apply}: the leagues and tier overrides whose
 * snapshots are overwritten with the blueprint's rules. A listed tier without an override gets one.
 */
@Getter
@Setter
public class ApplyBlueprintRequest {
    private List<String> leagueIds;
    private List<String> tierIds;
}
