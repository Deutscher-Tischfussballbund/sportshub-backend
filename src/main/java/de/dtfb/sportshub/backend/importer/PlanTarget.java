package de.dtfb.sportshub.backend.importer;

/**
 * What a planned record resolves to for the records depending on it: an existing entity, one the run
 * creates ({@code entityId} null), or nothing usable ({@code rejected}).
 */
record PlanTarget(String entityId, boolean rejected) {
    static final PlanTarget CREATED = new PlanTarget(null, false);
    static final PlanTarget REJECTED = new PlanTarget(null, true);
}
