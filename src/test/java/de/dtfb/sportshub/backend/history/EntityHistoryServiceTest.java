package de.dtfb.sportshub.backend.history;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

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
}
