package de.dtfb.sportshub.backend.importer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.dtfb.sportshub.backend.importer.sportsmanager.SportsManagerImportSource;
import de.dtfb.sportshub.backend.player.PlayerGender;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The SM adapter only normalizes -- trimming, blanks, gender codes, flags, dates (docs/28). */
class SportsManagerImportSourceTest {

    private final SportsManagerImportSource source =
        new SportsManagerImportSource(new ObjectMapper().registerModule(new JavaTimeModule()));

    @Test
    void parsesTheFixture() throws Exception {
        ImportBatch batch;
        try (InputStream in = getClass().getResourceAsStream("/import/sm-export-v1.json")) {
            batch = source.parse(in, "sm-export-v1.json");
        }

        assertThat(batch.header().instance()).isEqualTo("tfvhh");
        assertThat(batch.header().anonymized()).isTrue();
        assertThat(batch.header().exportedAt()).isEqualTo(Instant.parse("2026-10-10T12:00:00Z"));

        assertThat(batch.federations()).containsExactly(new ImportedFederation("1", "Hamburg"));
        assertThat(batch.clubs().getFirst())
            .isEqualTo(new ImportedClub("10", "Kickerfreunde Alster", "KFA", "Hamburg", "1", true));
        assertThat(batch.clubs().get(1).shortName()).isNull();
        assertThat(batch.clubs().get(1).active()).isFalse();

        ImportedPlayer maren = batch.players().getFirst();
        assertThat(maren.gender()).isEqualTo(PlayerGender.FEMALE);
        assertThat(maren.nationalLicense()).isEqualTo("B");
        assertThat(maren.internationalId()).isEqualTo("ITSF-1");
        ImportedPlayer olaf = batch.players().get(1);
        assertThat(olaf.number()).isNull();
        assertThat(olaf.birthYear()).as("0 = unknown in the SM").isNull();
        assertThat(batch.players().get(2).firstName()).isNull();
        ImportedPlayer kim = batch.players().get(3);
        assertThat(kim.gender()).isNull();
        assertThat(kim.genderRaw()).isEqualTo("X");

        assertThat(batch.memberships().getFirst().joinedAt()).isEqualTo(Instant.parse("2015-03-01T00:00:00Z"));
        assertThat(batch.memberships().getFirst().left()).isFalse();
        assertThat(batch.memberships().get(1).left()).isTrue();
        assertThat(batch.memberships().get(1).leftAt()).as("SM's zero date").isNull();
    }

    @Test
    void refusesOtherFormats() {
        assertThatThrownBy(() -> source.parse(stream("{\"format\": \"something\"}"), "x.json"))
            .isInstanceOf(ImportFormatException.class);
        assertThatThrownBy(() -> source.parse(stream("not json"), "x.json"))
            .isInstanceOf(ImportFormatException.class);
        assertThatThrownBy(() -> source.parse(
            stream("{\"format\": \"sportshub-sm-export\", \"version\": 2, \"instance\": \"x\"}"), "x.json"))
            .isInstanceOf(ImportFormatException.class);
    }

    private static InputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
