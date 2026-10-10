package de.dtfb.sportshub.backend.importer;

import de.dtfb.sportshub.backend.group.Group;
import de.dtfb.sportshub.backend.matchday.MatchDay;
import de.dtfb.sportshub.backend.standing.Standing;
import de.dtfb.sportshub.backend.standing.StandingService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Undoes an applied run (docs/28) by replaying its journal ({@link ImportChange}) backwards: created records
 * are deleted, changed fields get their old value back. All or nothing, refused with {@link UndoBlocker}s when
 * something outside the run depends on it -- a later run wrote the same records, a changed field was changed
 * again, or a created record is used by other data (every association in the model is checked, plus
 * references from other source installations).
 */
@Service
public class ImportUndoService {

    @PersistenceContext
    private EntityManager entityManager;

    private final ImportRunRepository runRepository;
    private final ImportChangeRepository changeRepository;
    private final ExternalReferenceRepository referenceRepository;
    private final StandingService standingService;
    private final ImportService importService;
    private final DefaultFormattingConversionService conversion = new DefaultFormattingConversionService();
    private final ImportRunGate gate;
    private final TransactionTemplate transaction;

    public ImportUndoService(ImportRunRepository runRepository, ImportChangeRepository changeRepository,
                             ExternalReferenceRepository referenceRepository, StandingService standingService,
                             ImportService importService, ImportRunGate gate,
                             PlatformTransactionManager transactionManager) {
        this.runRepository = runRepository;
        this.changeRepository = changeRepository;
        this.referenceRepository = referenceRepository;
        this.standingService = standingService;
        this.importService = importService;
        this.gate = gate;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public UndoCheckDto check(String runId) {
        ImportRun run = requireApplied(runId);
        List<ImportChange> changes = changeRepository.findByRunIdOrderBySeqAsc(runId);
        Set<String> createdIds = new java.util.HashSet<>();
        changes.stream().filter(c -> c.getOperation() == ImportChangeOperation.CREATE)
            .forEach(c -> createdIds.add(c.getEntityId()));
        // Changed fields of records that existed before the run -- the run's own new records simply go.
        int changed = (int) changes.stream()
            .filter(c -> c.getOperation() == ImportChangeOperation.UPDATE && !createdIds.contains(c.getEntityId()))
            .map(c -> c.getEntityType() + ":" + c.getEntityId() + ":" + c.getField()).distinct().count();
        int created = createdIds.size();
        List<UndoBlocker> blockers = blockers(run, changes);
        return new UndoCheckDto(blockers.isEmpty(), created, changed, blockers);
    }

    /**
     * Undoes the run -- marked UNDOING first, in a transaction of its own, so everyone sees it running; back to
     * APPLIED if the undo fails or is refused.
     */
    public ImportRunDto undo(String runId, String actor) {
        requireApplied(runId);
        if (!gate.claim(runId, ImportRunStatus.APPLIED, ImportRunStatus.UNDOING)) {
            throw importService.busyOrClosed(runId);
        }
        ImportRunDto undone;
        try {
            undone = transaction.execute(tx -> undoClaimed(runId, actor));
        } catch (RuntimeException e) {
            gate.release(runId, ImportRunStatus.UNDOING, ImportRunStatus.APPLIED);
            throw e;
        }
        importService.refreshOpenPreviewsLater(runId);
        return undone;
    }

    private ImportRunDto undoClaimed(String runId, String actor) {
        ImportRun run = runRepository.findById(runId).orElseThrow(() -> new ImportRunNotFoundException(runId));
        List<ImportChange> changes = changeRepository.findByRunIdOrderBySeqAsc(runId);
        List<UndoBlocker> blockers = blockers(run, changes);
        if (!blockers.isEmpty()) {
            throw new ImportUndoBlockedException(blockers);
        }

        Map<String, Group> groups = affectedGroups(changes);
        Set<String> deletedIds = new LinkedHashSet<>();
        changes.stream().filter(c -> c.getOperation() == ImportChangeOperation.CREATE)
            .forEach(c -> deletedIds.add(c.getEntityId()));
        // The standings cache of a group going away (or losing fixtures) is rebuilt -- drop it first.
        for (Group group : groups.values()) {
            entityManager.createQuery("DELETE FROM Standing s WHERE s.group.id = :groupId")
                .setParameter("groupId", group.getId()).executeUpdate();
        }

        for (int i = changes.size() - 1; i >= 0; i--) {
            ImportChange change = changes.get(i);
            Class<?> type = entityClass(change.getEntityType());
            Object entity = entityManager.find(type, change.getEntityId());
            if (entity == null) continue;
            if (change.getOperation() == ImportChangeOperation.CREATE) {
                // Deletes go out in the order of remove() -- newest first -- at the single flush below.
                entityManager.remove(entity);
            } else if (change.getOperation() == ImportChangeOperation.UPDATE) {
                write(entity, change.getField(), change.getOldValue());
            }
        }
        entityManager.flush();
        groups.values().stream()
            .filter(group -> !deletedIds.contains(group.getId()))
            .forEach(group -> standingService.recompute(entityManager.find(Group.class, group.getId())));

        run.setStatus(ImportRunStatus.UNDONE);
        run.setUndoneAt(Instant.now());
        run.setUndoneByDtfbId(actor);
        return importService.toDto(runRepository.save(run));
    }

    //region blockers
    private List<UndoBlocker> blockers(ImportRun run, List<ImportChange> changes) {
        List<UndoBlocker> blockers = new ArrayList<>();
        if (changes.isEmpty()) {
            blockers.add(new UndoBlocker(UndoBlocker.Code.NO_JOURNAL, null, null, null, null));
            return blockers;
        }
        changes.stream().filter(c -> c.getOperation() == ImportChangeOperation.DELETE).findFirst()
            .ifPresent(c -> blockers.add(new UndoBlocker(UndoBlocker.Code.REPLACED_DATA, c.getEntityType(),
                c.getEntityId(), null, null)));
        for (ImportRun later : changeRepository.laterRunsTouching(run.getId(), run.getFinishedAt())) {
            blockers.add(new UndoBlocker(UndoBlocker.Code.LATER_RUN, "ImportRun", later.getId(), later.getFilename(),
                Objects.toString(later.getFinishedAt(), null)));
        }
        blockers.addAll(changedSince(changes));
        blockers.addAll(inUse(run, changes));
        return blockers;
    }

    /** Fields the run changed whose value isn't the run's any more -- someone changed them again. */
    private List<UndoBlocker> changedSince(List<ImportChange> changes) {
        Set<String> created = new java.util.HashSet<>();
        Map<String, ImportChange> lastChange = new LinkedHashMap<>();
        for (ImportChange change : changes) {
            if (change.getOperation() == ImportChangeOperation.CREATE) created.add(change.getEntityId());
            if (change.getOperation() == ImportChangeOperation.UPDATE && !created.contains(change.getEntityId())) {
                lastChange.put(change.getEntityType() + ":" + change.getEntityId() + ":" + change.getField(), change);
            }
        }
        List<UndoBlocker> blockers = new ArrayList<>();
        for (ImportChange change : lastChange.values()) {
            Object entity = entityManager.find(entityClass(change.getEntityType()), change.getEntityId());
            String current = entity == null ? null : read(entity, change.getField());
            if (entity == null || !Objects.equals(current, change.getNewValue())) {
                blockers.add(new UndoBlocker(UndoBlocker.Code.CHANGED_SINCE, change.getEntityType(),
                    change.getEntityId(), label(entity), change.getField()));
            }
        }
        return blockers;
    }

    /**
     * Records the run created that something outside the run refers to: every association in the model
     * pointing at a created record from a record the run didn't create, and references of other source
     * installations. The standings cache doesn't count -- it is rebuilt.
     */
    private List<UndoBlocker> inUse(ImportRun run, List<ImportChange> changes) {
        Map<String, Set<String>> createdByType = new HashMap<>();
        changes.stream().filter(c -> c.getOperation() == ImportChangeOperation.CREATE)
            .forEach(c -> createdByType.computeIfAbsent(c.getEntityType(), k -> new LinkedHashSet<>()).add(c.getEntityId()));
        List<UndoBlocker> blockers = new ArrayList<>();
        for (EntityType<?> owner : entityManager.getMetamodel().getEntities()) {
            if (owner.getJavaType() == Standing.class || owner.getJavaType() == ImportChange.class) continue;
            for (Attribute<?, ?> attribute : owner.getAttributes()) {
                if (!(attribute instanceof SingularAttribute<?, ?> singular)
                    || singular.getPersistentAttributeType() != Attribute.PersistentAttributeType.MANY_TO_ONE) continue;
                String targetType = singular.getJavaType().getSimpleName();
                if (!createdByType.containsKey(targetType)) continue;
                List<?> rows = entityManager.createQuery(
                        "SELECT e.id, e." + attribute.getName() + ".id FROM " + owner.getName() + " e"
                            + " WHERE e." + attribute.getName() + ".id IN (SELECT c.entityId FROM ImportChange c"
                            + "   WHERE c.run.id = :runId AND c.operation = :create AND c.entityType = :type)"
                            + " AND e.id NOT IN (SELECT c2.entityId FROM ImportChange c2"
                            + "   WHERE c2.run.id = :runId AND c2.operation = :create)")
                    .setParameter("runId", run.getId())
                    .setParameter("create", ImportChangeOperation.CREATE)
                    .setParameter("type", targetType)
                    .setMaxResults(20)
                    .getResultList();
                for (Object row : rows) {
                    Object[] columns = (Object[]) row;
                    String targetId = (String) columns[1];
                    blockers.add(new UndoBlocker(UndoBlocker.Code.IN_USE, targetType, targetId,
                        label(entityManager.find(singular.getJavaType(), targetId)), owner.getName()));
                }
            }
        }
        Set<String> created = new java.util.HashSet<>();
        createdByType.values().forEach(created::addAll);
        Set<String> ownReferences = createdByType.getOrDefault("ExternalReference", Set.of());
        referenceRepository.findAll().stream()
            .filter(r -> created.contains(r.getEntityId()) && !ownReferences.contains(r.getId()))
            .limit(20)
            .forEach(r -> blockers.add(new UndoBlocker(UndoBlocker.Code.IN_USE, r.getEntityType().name(),
                r.getEntityId(), null, "ExternalReference:" + r.getSource() + "/" + r.getInstance())));
        return blockers;
    }
    //endregion

    //region helpers
    /** Groups whose standings depend on what the run wrote: its groups and those of its fixtures. */
    private Map<String, Group> affectedGroups(List<ImportChange> changes) {
        Map<String, Group> groups = new LinkedHashMap<>();
        for (ImportChange change : changes) {
            if (change.getEntityType().equals("Group")) {
                Group group = entityManager.find(Group.class, change.getEntityId());
                if (group != null) groups.put(group.getId(), group);
            }
            if (change.getEntityType().equals("MatchDay")) {
                MatchDay matchDay = entityManager.find(MatchDay.class, change.getEntityId());
                if (matchDay != null && matchDay.getRound() != null && matchDay.getRound().getGroup() != null) {
                    groups.put(matchDay.getRound().getGroup().getId(), matchDay.getRound().getGroup());
                }
            }
        }
        return groups;
    }

    private Class<?> entityClass(String simpleName) {
        return entityManager.getMetamodel().getEntities().stream()
            .filter(e -> e.getJavaType().getSimpleName().equals(simpleName))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Unknown entity type " + simpleName))
            .getJavaType();
    }

    private String read(Object entity, String field) {
        return ImportJournal.text(wrapper(entity).getPropertyValue(field));
    }

    /** Sets a field from its journaled text: an association by id, anything else converted from text. */
    private void write(Object entity, String field, String value) {
        BeanWrapper wrapper = wrapper(entity);
        Class<?> type = wrapper.getPropertyType(field);
        boolean association = type != null && entityManager.getMetamodel().getEntities().stream()
            .anyMatch(e -> e.getJavaType().equals(type));
        if (association) {
            wrapper.setPropertyValue(field, value == null ? null : entityManager.getReference(type, value));
        } else {
            wrapper.setPropertyValue(field, value);
        }
    }

    private BeanWrapper wrapper(Object entity) {
        BeanWrapperImpl wrapper = new BeanWrapperImpl(entity);
        wrapper.setConversionService(conversion);
        return wrapper;
    }

    /** A readable name for a blocker, where the record has one. */
    private static String label(Object entity) {
        if (entity == null) return null;
        BeanWrapperImpl wrapper = new BeanWrapperImpl(entity);
        if (wrapper.isReadableProperty("firstName") && wrapper.isReadableProperty("lastName")) {
            return wrapper.getPropertyValue("firstName") + " " + wrapper.getPropertyValue("lastName");
        }
        if (wrapper.isReadableProperty("name")) {
            return Objects.toString(wrapper.getPropertyValue("name"), null);
        }
        return null;
    }

    private ImportRun requireApplied(String runId) {
        ImportRun run = runRepository.findById(runId).orElseThrow(() -> new ImportRunNotFoundException(runId));
        if (run.getStatus() != ImportRunStatus.APPLIED) {
            throw importService.busyOrClosed(runId);
        }
        return run;
    }
    //endregion
}
