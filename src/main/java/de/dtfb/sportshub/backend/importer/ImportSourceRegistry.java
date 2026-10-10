package de.dtfb.sportshub.backend.importer;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** All {@link ImportSource} beans by key. */
@Component
public class ImportSourceRegistry {

    private final Map<String, ImportSource> sources = new LinkedHashMap<>();

    public ImportSourceRegistry(List<ImportSource> sources) {
        sources.forEach(source -> this.sources.put(source.key(), source));
    }

    public ImportSource get(String key) {
        ImportSource source = sources.get(key);
        if (source == null) {
            throw new ImportFormatException("Unknown import source: " + key);
        }
        return source;
    }

    public Collection<ImportSource> all() {
        return sources.values();
    }
}
