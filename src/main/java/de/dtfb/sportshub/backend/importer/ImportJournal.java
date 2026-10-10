package de.dtfb.sportshub.backend.importer;

import de.dtfb.sportshub.backend.base.BaseEntity;
import de.dtfb.sportshub.backend.player.PlayerNumberSequence;
import de.dtfb.sportshub.backend.standing.Standing;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostDeleteEventListener;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostInsertEventListener;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.hibernate.persister.entity.EntityPersister;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Records every write of an apply (docs/28, undo) from Hibernate's own insert/update/delete events -- so the
 * writers need no bookkeeping and nothing slips through. Only active between {@link #start()} and
 * {@link #stop()} on the applying thread. Left out: the importer's own records, the standings cache (rebuilt
 * after an undo) and the number sequences (issued numbers are never reused).
 */
@Component
public class ImportJournal implements PostInsertEventListener, PostUpdateEventListener, PostDeleteEventListener {

    /** A recorded write, before it is stored as an {@link ImportChange}. */
    record Entry(String entityType, String entityId, ImportChangeOperation operation, String field, String oldValue,
                 String newValue) {
    }

    private static final Set<Class<?>> IGNORED = Set.of(ImportRun.class, ImportItem.class, ImportChange.class,
        Standing.class, PlayerNumberSequence.class);

    private final ThreadLocal<List<Entry>> entries = new ThreadLocal<>();
    private final EntityManagerFactory entityManagerFactory;

    public ImportJournal(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    @PostConstruct
    void register() {
        var registry = entityManagerFactory.unwrap(SessionFactoryImplementor.class).getEventListenerRegistry();
        registry.appendListeners(EventType.POST_INSERT, this);
        registry.appendListeners(EventType.POST_UPDATE, this);
        registry.appendListeners(EventType.POST_DELETE, this);
    }

    void start() {
        entries.set(new ArrayList<>());
    }

    /** The writes since {@link #start()}, in order; the journal is inactive afterwards. */
    List<Entry> stop() {
        List<Entry> recorded = entries.get();
        entries.remove();
        return recorded == null ? List.of() : recorded;
    }

    @Override
    public void onPostInsert(PostInsertEvent event) {
        List<Entry> recorded = active(event.getEntity());
        if (recorded == null) return;
        recorded.add(new Entry(type(event.getEntity()), Objects.toString(event.getId()), ImportChangeOperation.CREATE,
            null, null, null));
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        List<Entry> recorded = active(event.getEntity());
        if (recorded == null || event.getOldState() == null) return;
        String[] names = event.getPersister().getPropertyNames();
        for (int i = 0; i < names.length; i++) {
            String before = text(event.getOldState()[i]);
            String after = text(event.getState()[i]);
            if (!Objects.equals(before, after)) {
                recorded.add(new Entry(type(event.getEntity()), Objects.toString(event.getId()),
                    ImportChangeOperation.UPDATE, names[i], before, after));
            }
        }
    }

    @Override
    public void onPostDelete(PostDeleteEvent event) {
        List<Entry> recorded = active(event.getEntity());
        if (recorded == null) return;
        recorded.add(new Entry(type(event.getEntity()), Objects.toString(event.getId()), ImportChangeOperation.DELETE,
            null, null, null));
    }

    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return false;
    }

    private List<Entry> active(Object entity) {
        List<Entry> recorded = entries.get();
        return recorded == null || IGNORED.contains(entity.getClass()) ? null : recorded;
    }

    private static String type(Object entity) {
        return entity.getClass().getSimpleName();
    }

    /**
     * A property value as stored text: an associated entity by its id, an enum by name, a time at the
     * database's microsecond precision (so a value read back compares equal), else its text form.
     */
    static String text(Object value) {
        if (value == null) return null;
        if (value instanceof BaseEntity entity) return entity.getId();
        if (value instanceof Enum<?> e) return e.name();
        // Rounded like MySQL datetime(6) and H2 store it.
        if (value instanceof Instant instant) return instant.plusNanos(500).truncatedTo(ChronoUnit.MICROS).toString();
        return value.toString();
    }
}
