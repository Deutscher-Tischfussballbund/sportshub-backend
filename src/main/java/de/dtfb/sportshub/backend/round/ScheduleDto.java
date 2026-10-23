package de.dtfb.sportshub.backend.round;

import de.dtfb.sportshub.backend.group.GroupKind;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** A group's generated plan for display: its rounds in order, each with its fixtures, plus the
 * names the page header needs. Read-only view over Round/MatchDay; see docs/12 §4. */
@Getter
@Setter
public class ScheduleDto {
    private String groupId;
    private String groupName;
    private String tierId;
    private String tierName;
    /** {@code KNOCKOUT}: the fixtures are a bracket fed by sources (docs/34) -- no table. */
    private GroupKind groupKind;
    private String leagueId;
    private String leagueName;
    private List<ScheduleRoundDto> rounds;
}
