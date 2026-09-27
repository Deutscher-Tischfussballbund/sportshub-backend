package de.dtfb.sportshub.backend.round;

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
    private String tierName;
    private String leagueId;
    private String leagueName;
    private List<ScheduleRoundDto> rounds;
}
