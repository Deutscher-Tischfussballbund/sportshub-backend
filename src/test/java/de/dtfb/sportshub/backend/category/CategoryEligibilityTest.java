package de.dtfb.sportshub.backend.category;

import de.dtfb.sportshub.backend.player.LeagueSide;
import de.dtfb.sportshub.backend.player.Player;
import de.dtfb.sportshub.backend.player.PlayerGender;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The full gender x eligible-side matrix of {@link CategoryEligibility} (docs/19). */
class CategoryEligibilityTest {

    private final CategoryEligibility eligibility = new CategoryEligibility();

    @Test
    void openCategory_admitsEveryone() {
        Category open = category(null);
        for (PlayerGender gender : PlayerGender.values()) {
            assertThat(eligibility.check(open, player(gender))).isEmpty();
        }
        assertThat(eligibility.check(null, player(PlayerGender.FEMALE))).isEmpty();
    }

    @Test
    void womensSide_admitsFemaleAndDiversWomen_only() {
        Category women = category(LeagueSide.WOMEN);
        assertThat(eligibility.check(women, player(PlayerGender.FEMALE))).isEmpty();
        assertThat(eligibility.check(women, player(PlayerGender.DIVERSE_WOMEN))).isEmpty();
        assertThat(eligibility.check(women, player(PlayerGender.MALE))).contains(IneligibilityReason.WRONG_SIDE);
        assertThat(eligibility.check(women, player(PlayerGender.DIVERSE_MEN))).contains(IneligibilityReason.WRONG_SIDE);
    }

    @Test
    void mensSide_admitsMaleAndDiversMen_only() {
        Category men = category(LeagueSide.MEN);
        assertThat(eligibility.check(men, player(PlayerGender.MALE))).isEmpty();
        assertThat(eligibility.check(men, player(PlayerGender.DIVERSE_MEN))).isEmpty();
        assertThat(eligibility.check(men, player(PlayerGender.FEMALE))).contains(IneligibilityReason.WRONG_SIDE);
        assertThat(eligibility.check(men, player(PlayerGender.DIVERSE_WOMEN))).contains(IneligibilityReason.WRONG_SIDE);
    }

    private static Category category(LeagueSide side) {
        Category category = new Category();
        category.setEligibleSide(side);
        return category;
    }

    private static Player player(PlayerGender gender) {
        Player player = new Player();
        player.setGender(gender);
        return player;
    }
}
