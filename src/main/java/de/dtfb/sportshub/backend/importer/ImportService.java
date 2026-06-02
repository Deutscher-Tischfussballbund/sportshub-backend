package de.dtfb.sportshub.backend.importer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dtfb.sportshub.backend.federation.Federation;
import de.dtfb.sportshub.backend.importer.data.ImportPayload;
import de.dtfb.sportshub.backend.importer.data.ImportSeason;
import de.dtfb.sportshub.backend.importer.importers.FederationImporter;
import de.dtfb.sportshub.backend.importer.importers.SeasonImporter;
import de.dtfb.sportshub.backend.util.IdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.io.IOException;
import java.io.InputStream;

@Service
public class ImportService {
    private final FederationImporter federationImporter;
    private final SeasonImporter seasonImporter;
    private final ObjectMapper objectMapper;

    public ImportService(FederationImporter federationImporter, SeasonImporter seasonImporter, ObjectMapper objectMapper) {
        this.federationImporter = federationImporter;
        this.seasonImporter = seasonImporter;
        this.objectMapper = objectMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public ImportResult importData(InputStream input) throws IOException {
        ImportResult result = new ImportResult();
        JsonParser parser = objectMapper.getFactory().createParser(input);

        while (parser.nextToken() != null) {
            ImportPayload payload;
            try {
                payload = objectMapper.readValue(parser, ImportPayload.class);
            } catch (Exception ex) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                result.setMessage(ex.getMessage());
                return result;
            }

            Federation federation = federationImporter.importFederation(payload.getMeta());

            String importId = IdGenerator.newId();
            for (ImportSeason season : payload.getSeasons()) {
                seasonImporter.importSeason(season, federation, importId);
                result.incrementSuccess();
            }
        }

        return result;
    }
}
