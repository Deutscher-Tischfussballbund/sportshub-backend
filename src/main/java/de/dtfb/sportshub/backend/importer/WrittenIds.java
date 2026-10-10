package de.dtfb.sportshub.backend.importer;

/** During one apply: the Sports Hub id a source record has (written now or by an earlier run), and linking new ones. */
interface WrittenIds {

    /** Null if the record is neither written in this apply nor known from an earlier run. */
    String id(ImportRecordType type, String externalId);

    /** Records (or refreshes) the {@link ExternalReference} of a written or confirmed record. */
    void link(ImportRecordType type, String externalId, String entityId);
}
