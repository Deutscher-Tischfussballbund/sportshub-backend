package de.dtfb.sportshub.backend.importer;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Marks a run as busy (applying, undoing) in a short transaction of its own, committed before the long
 * one starts -- so every other request sees at once that the run is being worked on, and a second apply
 * is refused instead of waiting on locks (docs/28). A run left busy by a crash is released on startup.
 */
@Component
class ImportRunGate implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ImportRunGate.class);

    @PersistenceContext
    private EntityManager entityManager;

    private final TransactionTemplate ownTransaction;

    ImportRunGate(PlatformTransactionManager transactionManager) {
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Moves the run from {@code from} to {@code busy}; false if it isn't in {@code from} (any more). */
    boolean claim(String runId, ImportRunStatus from, ImportRunStatus busy) {
        Integer updated = ownTransaction.execute(tx -> move(runId, from, busy));
        return updated != null && updated == 1;
    }

    /** Back from {@code busy} to {@code back} -- after the long transaction failed and rolled back. */
    void release(String runId, ImportRunStatus busy, ImportRunStatus back) {
        ownTransaction.executeWithoutResult(tx -> move(runId, busy, back));
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer applying = ownTransaction.execute(tx -> moveAll(ImportRunStatus.APPLYING, ImportRunStatus.PREVIEWED));
        Integer undoing = ownTransaction.execute(tx -> moveAll(ImportRunStatus.UNDOING, ImportRunStatus.APPLIED));
        if ((applying != null && applying > 0) || (undoing != null && undoing > 0)) {
            log.warn("Import runs interrupted by a restart released: {} applying, {} undoing", applying, undoing);
        }
    }

    private int move(String runId, ImportRunStatus from, ImportRunStatus to) {
        return entityManager.createQuery("UPDATE ImportRun r SET r.status = :to WHERE r.id = :id AND r.status = :from")
            .setParameter("to", to).setParameter("id", runId).setParameter("from", from)
            .executeUpdate();
    }

    private int moveAll(ImportRunStatus from, ImportRunStatus to) {
        return entityManager.createQuery("UPDATE ImportRun r SET r.status = :to WHERE r.status = :from")
            .setParameter("to", to).setParameter("from", from)
            .executeUpdate();
    }
}
