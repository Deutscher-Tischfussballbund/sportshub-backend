package de.dtfb.sportshub.backend.matchday;

import de.dtfb.sportshub.backend.leaguerules.RaceEndRule;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link RaceScoring}: the Race to 42 rules of docs/22 -- seven segments of six, two end rules. */
class RaceScoringTest {

    private static final RaceScoring.Rules DRAW = new RaceScoring.Rules(42, 6, RaceEndRule.DRAW_ALLOWED);
    private static final RaceScoring.Rules KO = new RaceScoring.Rules(42, 6, RaceEndRule.TWO_POINT_LEAD);

    /** Seven segments; the given running scores fill them from the first, the rest stay empty. */
    private static List<RaceScoring.Segment> race(int... scores) {
        List<RaceScoring.Segment> segments = new ArrayList<>();
        for (int i = 0; i < scores.length; i += 2) {
            segments.add(new RaceScoring.Segment(scores[i], scores[i + 1]));
        }
        while (segments.size() < 7) {
            segments.add(new RaceScoring.Segment(null, null));
        }
        return segments;
    }

    @Test
    void aSegmentEndsWhenEitherSideReachesTheStep() {
        assertThat(RaceScoring.violation(race(6, 4, 12, 9), DRAW)).isNull();
        // the trailing side takes the third segment: 12 : 4 -> (12 + y) : 18
        assertThat(RaceScoring.violation(race(6, 4, 12, 4, 15, 18), DRAW)).isNull();
    }

    @Test
    void aSegmentMustEndExactlyAtItsStep() {
        assertThat(RaceScoring.violation(race(5, 4), DRAW)).contains("reaches 6");
        assertThat(RaceScoring.violation(race(7, 4), DRAW)).contains("reaches 6");
        assertThat(RaceScoring.violation(race(6, 6), DRAW)).contains("reaches 6");
        assertThat(RaceScoring.violation(race(6, 4, 12, 13), DRAW)).contains("reaches 12");
    }

    @Test
    void theRunningScoreNeverGoesDown_andSegmentsGoInOrder() {
        assertThat(RaceScoring.violation(race(6, 4, 12, 3), DRAW)).contains("can't go down");
        List<RaceScoring.Segment> gap = race(6, 4);
        gap.set(2, new RaceScoring.Segment(18, 10));
        assertThat(RaceScoring.violation(gap, DRAW)).contains("in order");
    }

    @Test
    void drawAllowed_endsAt42_orAsA41to41Draw() {
        int[] upTo36 = {6, 4, 12, 9, 18, 15, 24, 20, 30, 27, 36, 33};
        assertThat(RaceScoring.decided(race(concat(upTo36, 42, 38)), DRAW)).isTrue();
        assertThat(RaceScoring.decided(race(concat(upTo36, 41, 42)), DRAW)).isTrue();
        assertThat(RaceScoring.decided(race(concat(upTo36, 41, 41)), DRAW)).isTrue();
        assertThat(RaceScoring.violation(race(concat(upTo36, 42, 42)), DRAW)).contains("draw at 41 : 41");
        assertThat(RaceScoring.violation(race(concat(upTo36, 40, 39)), DRAW)).isNotNull();
    }

    @Test
    void twoPointLead_playsOnPast42() {
        int[] upTo36 = {6, 4, 12, 9, 18, 15, 24, 20, 30, 27, 36, 33};
        assertThat(RaceScoring.decided(race(concat(upTo36, 42, 38)), KO)).isTrue();
        assertThat(RaceScoring.decided(race(concat(upTo36, 43, 41)), KO)).isTrue();
        assertThat(RaceScoring.decided(race(concat(upTo36, 44, 46)), KO)).isTrue();
        assertThat(RaceScoring.violation(race(concat(upTo36, 42, 41)), KO)).contains("two-point lead");
        assertThat(RaceScoring.violation(race(concat(upTo36, 41, 41)), KO)).isNotNull();
        assertThat(RaceScoring.violation(race(concat(upTo36, 45, 41)), KO)).isNotNull(); // would have ended at 43 : 41
    }

    @Test
    void notDecided_untilTheLastSegmentIsIn() {
        assertThat(RaceScoring.decided(race(6, 4, 12, 9), DRAW)).isFalse();
    }

    @Test
    void aSegmentScoredZuNull_isJustAValidStep() {
        // late team: the opponent gets the whole step, the late side stays where it was
        assertThat(RaceScoring.violation(race(6, 4, 12, 4), DRAW)).isNull();
    }

    private static int[] concat(int[] head, int... tail) {
        int[] all = new int[head.length + tail.length];
        System.arraycopy(head, 0, all, 0, head.length);
        System.arraycopy(tail, 0, all, head.length, tail.length);
        return all;
    }
}
