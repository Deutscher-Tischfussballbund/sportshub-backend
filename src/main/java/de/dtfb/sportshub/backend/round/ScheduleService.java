package de.dtfb.sportshub.backend.round;

import de.dtfb.sportshub.backend.group.Group;
import de.dtfb.sportshub.backend.group.GroupNotFoundException;
import de.dtfb.sportshub.backend.group.GroupRepository;
import de.dtfb.sportshub.backend.matchday.MatchDay;
import de.dtfb.sportshub.backend.matchday.MatchDayRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Reads a group's plan as one nested view (rounds → fixtures) for the schedule page. */
@Service
public class ScheduleService {

    private static final Comparator<MatchDay> KICK_OFF_ORDER = Comparator
        .comparing(MatchDay::getStartDate)
        .thenComparing(matchDay -> matchDay.getTeamHome() == null ? "" : matchDay.getTeamHome().getName(),
            Comparator.nullsFirst(Comparator.naturalOrder()));

    private final GroupRepository groupRepository;
    private final RoundRepository roundRepository;
    private final MatchDayRepository matchDayRepository;

    public ScheduleService(GroupRepository groupRepository, RoundRepository roundRepository,
                           MatchDayRepository matchDayRepository) {
        this.groupRepository = groupRepository;
        this.roundRepository = roundRepository;
        this.matchDayRepository = matchDayRepository;
    }

    @Transactional(readOnly = true)
    public ScheduleDto getSchedule(String groupId) {
        Group group = groupRepository.findById(groupId)
            .orElseThrow(() -> new GroupNotFoundException(groupId));
        Map<String, List<MatchDay>> fixturesByRound = matchDayRepository.findByRoundGroupId(groupId).stream()
            .collect(Collectors.groupingBy(matchDay -> matchDay.getRound().getId()));

        ScheduleDto schedule = new ScheduleDto();
        schedule.setGroupId(group.getId());
        schedule.setGroupName(group.getName());
        if (group.getTier() != null) {
            schedule.setTierName(group.getTier().getName());
            if (group.getTier().getLeague() != null) {
                schedule.setLeagueId(group.getTier().getLeague().getId());
                schedule.setLeagueName(group.getTier().getLeague().getName());
            }
        }
        schedule.setRounds(roundRepository.findByGroupIdOrderByIndex(groupId).stream()
            .map(round -> toRound(round, fixturesByRound.getOrDefault(round.getId(), List.of())))
            .toList());
        return schedule;
    }

    private ScheduleRoundDto toRound(Round round, List<MatchDay> matchDays) {
        ScheduleRoundDto dto = new ScheduleRoundDto();
        dto.setId(round.getId());
        dto.setName(round.getName());
        dto.setIndex(round.getIndex());
        dto.setWindowStart(round.getWindowStart());
        dto.setWindowEnd(round.getWindowEnd());
        dto.setFixtures(matchDays.stream().sorted(KICK_OFF_ORDER).map(this::toFixture).toList());
        return dto;
    }

    private ScheduleFixtureDto toFixture(MatchDay matchDay) {
        ScheduleFixtureDto dto = new ScheduleFixtureDto();
        dto.setId(matchDay.getId());
        if (matchDay.getTeamHome() != null) {
            dto.setTeamHomeId(matchDay.getTeamHome().getId());
            dto.setTeamHomeName(matchDay.getTeamHome().getName());
        }
        if (matchDay.getTeamAway() != null) {
            dto.setTeamAwayId(matchDay.getTeamAway().getId());
            dto.setTeamAwayName(matchDay.getTeamAway().getName());
        }
        dto.setStartDate(matchDay.getStartDate());
        if (matchDay.getLocation() != null) {
            dto.setLocationId(matchDay.getLocation().getId());
            dto.setLocationName(matchDay.getLocation().getName());
        }
        dto.setSchedulingState(matchDay.getSchedulingState());
        dto.setResultState(matchDay.getResultState());
        dto.setBye(matchDay.isBye());
        return dto;
    }
}
