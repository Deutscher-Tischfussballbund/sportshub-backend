package de.dtfb.sportshub.backend.matchday;

import de.dtfb.sportshub.backend.leaguerules.LeagueRuleSet;
import de.dtfb.sportshub.backend.leaguerules.RaceEndRule;

import java.util.List;

/**
 * The Race to N rules of docs/22, as pure functions over a fixture's segments (the game plan's
 * entries, in order), each holding the <b>running score</b> after it -- or nothing yet. Segment k
 * (1-based) ends as soon as one side's running total reaches k * step; the last one ends under the end
 * rule at the target: {@code DRAW_ALLOWED} -- at the target, or as a draw one below it (41 : 41);
 * {@code TWO_POINT_LEAD} -- once a side is at or past the target and leads by exactly two (43 : 41),
 * or reaches the target with a lead of two or more.
 */
final class RaceScoring {

    /** A segment's running score; both null = not entered yet. */
    record Segment(Integer home, Integer away) {
        boolean entered() {
            return home != null && away != null;
        }
    }

    record Rules(int target, int step, RaceEndRule endRule) {
        /** From a RACE rule set; the step defaults to target / segments, the end rule to draw-allowed. */
        static Rules of(LeagueRuleSet rules, int segments) {
            int target = rules.getRaceTarget() != null ? rules.getRaceTarget() : 42;
            int step = rules.getRaceStep() != null && rules.getRaceStep() > 0
                ? rules.getRaceStep() : Math.max(1, target / Math.max(1, segments));
            RaceEndRule endRule = rules.getRaceEndRule() != null ? rules.getRaceEndRule() : RaceEndRule.DRAW_ALLOWED;
            return new Rules(target, step, endRule);
        }
    }

    private RaceScoring() {
    }

    /**
     * Why the entered segments are not a valid race so far, or {@code null} if they are. Segments must
     * be entered in order (no gaps), running totals never go down, every segment but the last ends
     * exactly at its step with the other side below it, and the last one ends under the end rule.
     */
    static String violation(List<Segment> segments, Rules rules) {
        int previousHome = 0;
        int previousAway = 0;
        boolean gap = false;
        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            int number = i + 1;
            if (!segment.entered()) {
                gap = true;
                continue;
            }
            if (gap) {
                return "Segment " + number + " is entered, but an earlier one is missing; enter the segments in order";
            }
            int home = segment.home();
            int away = segment.away();
            if (home < previousHome || away < previousAway) {
                return "Segment " + number + ": the running score can't go down (it was "
                    + previousHome + " : " + previousAway + ")";
            }
            boolean last = number == segments.size();
            if (!last) {
                int step = number * rules.step();
                if (Math.max(home, away) != step || Math.min(home, away) >= step) {
                    return "Segment " + number + " ends when one side reaches " + step
                        + "; enter the running score at that moment";
                }
            } else if (!validEnd(home, away, rules)) {
                return "The last segment ends " + (rules.endRule() == RaceEndRule.TWO_POINT_LEAD
                    ? "when one side is at " + rules.target() + " or more with a two-point lead"
                    : "at " + rules.target() + ", or as a draw at " + (rules.target() - 1) + " : " + (rules.target() - 1));
            }
            previousHome = home;
            previousAway = away;
        }
        return null;
    }

    /** Whether every segment is entered and the race is complete under the end rule. */
    static boolean decided(List<Segment> segments, Rules rules) {
        return !segments.isEmpty()
            && segments.stream().allMatch(Segment::entered)
            && violation(segments, rules) == null;
    }

    private static boolean validEnd(int home, int away, Rules rules) {
        int high = Math.max(home, away);
        int low = Math.min(home, away);
        int target = rules.target();
        if (rules.endRule() == RaceEndRule.TWO_POINT_LEAD) {
            return high >= target && (high == target ? high - low >= 2 : high - low == 2);
        }
        return (high == target && low < target) || (home == away && home == target - 1);
    }
}
