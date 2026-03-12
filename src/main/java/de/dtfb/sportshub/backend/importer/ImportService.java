package de.dtfb.sportshub.backend.importer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dtfb.sportshub.backend.federation.Federation;
import de.dtfb.sportshub.backend.importer.data.ImportPayload;
import de.dtfb.sportshub.backend.importer.data.ImportSeason;
import de.dtfb.sportshub.backend.importer.importers.FederationImporter;
import de.dtfb.sportshub.backend.importer.importers.SeasonImporter;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

@Service
public class ImportService {
    private final FederationImporter federationImporter;
    private final SeasonImporter seasonImporter;

    public ImportService(FederationImporter federationImporter, SeasonImporter seasonImporter) {
        this.federationImporter = federationImporter;
        this.seasonImporter = seasonImporter;
    }

    public ImportResult importData(InputStream input) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        ImportResult result = new ImportResult();
        JsonParser parser = objectMapper.getFactory().createParser(input);

        while (!parser.isClosed()) {

            ImportPayload payload = objectMapper.readValue(parser, ImportPayload.class);

            Federation federation = federationImporter.importFederation(payload.getMeta());

            for (ImportSeason season : payload.getSeasons()) {
                seasonImporter.importSeason(season, federation);
                result.incrementSuccess();
            }
        }

        return result;
    }
}
