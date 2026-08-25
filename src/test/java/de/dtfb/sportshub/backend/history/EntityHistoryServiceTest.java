package de.dtfb.sportshub.backend.history;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EntityHistoryServiceTest {

    @Autowired
    private EntityHistoryService service;

    @Test
    void track_isANoOp_whenValuesAreEqual() {
        ChangeSet changes = ChangeSet.forEntity(HistoryEntityType.PLAYER, NanoIdUtils.randomNanoId())
            .track("firstName", "Lukas", "Lukas");

        assertThat(changes.isEmpty()).isTrue();
    }

    @Test
    void record_persistsOnlyTheChangedFields() {
        String entityId = NanoIdUtils.randomNanoId();
        ChangeSet changes = ChangeSet.forEntity(HistoryEntityType.PLAYER, entityId)
            .track("firstName", "Lukas", "Lucas")
            .track("lastName", "Bauer", "Bauer"); // unchanged -- must not be recorded

        service.record(changes, "tester");

        List<EntityHistoryDto> history = service.history(HistoryEntityType.PLAYER, entityId);
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().fieldName()).isEqualTo("firstName");
        assertThat(history.getFirst().oldValue()).isEqualTo("Lukas");
        assertThat(history.getFirst().newValue()).isEqualTo("Lucas");
        assertThat(history.getFirst().changedByDtfbId()).isEqualTo("tester");
    }

    @Test
    void fieldsAsOf_reconstructsTheValueInEffectAtThatMoment() {
        String entityId = NanoIdUtils.randomNanoId();
        Instant before = Instant.now().minus(1, ChronoUnit.DAYS);

        service.record(ChangeSet.forEntity(HistoryEntityType.CLUB, entityId)
            .track("name", "Alter Name", "Neuer Name"), null);

        // asked as of before the rename -- reconstructs the old value
        Map<String, String> asOfBefore = service.fieldsAsOf(HistoryEntityType.CLUB, entityId, List.of("name"), before);
        assertThat(asOfBefore).containsEntry("name", "Alter Name");

        // asked as of now (after the rename) -- no change happened after "now", so the caller falls
        // back to the entity's current value; the map carries no override for this field
        Map<String, String> asOfNow = service.fieldsAsOf(HistoryEntityType.CLUB, entityId, List.of("name"), Instant.now());
        assertThat(asOfNow).doesNotContainKey("name");
    }
}
