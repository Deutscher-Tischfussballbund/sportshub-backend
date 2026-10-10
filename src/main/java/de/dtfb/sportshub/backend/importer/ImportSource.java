package de.dtfb.sportshub.backend.importer;

import java.io.InputStream;
import java.util.Set;

/**
 * One data source the importer can read (docs/28) -- the Sports Manager now, Kickertool and others
 * later. An adapter only turns a file into a neutral {@link ImportBatch}; matching, validation,
 * preview and apply are shared ({@link ImportPlanner}, {@link ImportService}). Implementations are
 * Spring beans, collected by {@link ImportSourceRegistry}.
 */
public interface ImportSource {

    /** Stable key, stored on runs and external references -- e.g. {@code sportsmanager}. */
    String key();

    /** The record types this source delivers. */
    Set<ImportRecordType> supports();

    /**
     * Parses one uploaded file.
     *
     * @throws ImportFormatException if the file isn't in this source's format
     */
    ImportBatch parse(InputStream in, String filename);
}
