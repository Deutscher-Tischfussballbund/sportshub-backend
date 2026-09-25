package de.dtfb.sportshub.backend.player;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerGenderTest {

    @Test
    void bothDiversVariants_collapseToDiverse_butKeepTheirLeagueSide() {
        assertThat(PlayerGender.MALE.toPublic()).isEqualTo(Gender.MALE);
        assertThat(PlayerGender.FEMALE.toPublic()).isEqualTo(Gender.FEMALE);
        assertThat(PlayerGender.DIVERSE_MEN.toPublic()).isEqualTo(Gender.DIVERSE);
        assertThat(PlayerGender.DIVERSE_WOMEN.toPublic()).isEqualTo(Gender.DIVERSE);

        assertThat(PlayerGender.DIVERSE_MEN.leagueSide()).isEqualTo(LeagueSide.MEN);
        assertThat(PlayerGender.DIVERSE_WOMEN.leagueSide()).isEqualTo(LeagueSide.WOMEN);
    }

    @Test
    void wireValues_roundTrip() {
        for (PlayerGender gender : PlayerGender.values()) {
            assertThat(PlayerGender.fromValue(gender.getValue())).isEqualTo(gender);
        }
    }
}
