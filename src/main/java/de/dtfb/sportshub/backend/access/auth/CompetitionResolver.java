package de.dtfb.sportshub.backend.access.auth;

import de.dtfb.sportshub.backend.group.Group;
import de.dtfb.sportshub.backend.group.GroupRepository;
import de.dtfb.sportshub.backend.league.League;
import de.dtfb.sportshub.backend.match.Match;
import de.dtfb.sportshub.backend.match.MatchRepository;
import de.dtfb.sportshub.backend.matchday.MatchDay;
import de.dtfb.sportshub.backend.matchday.MatchDayRepository;
import de.dtfb.sportshub.backend.matchevent.MatchEventRepository;
import de.dtfb.sportshub.backend.matchset.MatchSetRepository;
import de.dtfb.sportshub.backend.round.Round;
import de.dtfb.sportshub.backend.round.RoundRepository;
import de.dtfb.sportshub.backend.tier.Tier;
import de.dtfb.sportshub.backend.tier.TierRepository;
import org.springframework.stereotype.Component;

/**
 * Resolves any competition-data entity up to its owning {@link League}, walking the
 * {@code Tier -> League} spine (Group -> Tier, Round -> Group, MatchDay -> Round, ...). Every link
 * is a {@code @ManyToOne} (eagerly fetched), so the whole ancestor chain is populated by the single
 * {@code findById} load -- the same assumption the COMPETITION scope check already relies on.
 *
 * <p>Returns {@code null} when the entity or any ancestor is missing; callers treat an unresolved
 * league as "deny" (a global admin is short-circuited earlier, before resolution).
 */
@Component
public class CompetitionResolver {

    private final TierRepository tierRepository;
    private final GroupRepository groupRepository;
    private final RoundRepository roundRepository;
    private final MatchDayRepository matchDayRepository;
    private final MatchRepository matchRepository;
    private final MatchSetRepository matchSetRepository;
    private final MatchEventRepository matchEventRepository;

    public CompetitionResolver(TierRepository tierRepository,
                               GroupRepository groupRepository,
                               RoundRepository roundRepository,
                               MatchDayRepository matchDayRepository,
                               MatchRepository matchRepository,
                               MatchSetRepository matchSetRepository,
                               MatchEventRepository matchEventRepository) {
        this.tierRepository = tierRepository;
        this.groupRepository = groupRepository;
        this.roundRepository = roundRepository;
        this.matchDayRepository = matchDayRepository;
        this.matchRepository = matchRepository;
        this.matchSetRepository = matchSetRepository;
        this.matchEventRepository = matchEventRepository;
    }

    public League ofTier(String id) {
        return id == null ? null : leagueOf(tierRepository.findById(id).orElse(null));
    }

    public League ofGroup(String id) {
        return id == null ? null : leagueOf(groupRepository.findById(id).orElse(null));
    }

    public League ofRound(String id) {
        return id == null ? null : leagueOf(roundRepository.findById(id).orElse(null));
    }

    public League ofMatchDay(String id) {
        return id == null ? null : leagueOf(matchDayRepository.findById(id).orElse(null));
    }

    public League ofMatch(String id) {
        return id == null ? null : leagueOf(matchRepository.findById(id).orElse(null));
    }

    public League ofMatchSet(String id) {
        return id == null ? null
            : matchSetRepository.findById(id).map(ms -> leagueOf(ms.getMatch())).orElse(null);
    }

    public League ofMatchEvent(String id) {
        return id == null ? null
            : matchEventRepository.findById(id).map(me -> leagueOf(me.getMatch())).orElse(null);
    }

    // --- null-safe walk, one overload per spine level ---

    private League leagueOf(Tier tier) {
        return tier == null ? null : tier.getLeague();
    }

    private League leagueOf(Group group) {
        return group == null ? null : leagueOf(group.getTier());
    }

    private League leagueOf(Round round) {
        return round == null ? null : leagueOf(round.getGroup());
    }

    private League leagueOf(MatchDay matchDay) {
        return matchDay == null ? null : leagueOf(matchDay.getRound());
    }

    private League leagueOf(Match match) {
        return match == null ? null : leagueOf(match.getMatchDay());
    }
}
