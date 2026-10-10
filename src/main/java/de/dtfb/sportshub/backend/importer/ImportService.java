package de.dtfb.sportshub.backend.importer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dtfb.sportshub.backend.federation.FederationNotFoundException;
import de.dtfb.sportshub.backend.federation.FederationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The import pipeline shared by every {@link ImportSource} (docs/28): upload = parse + plan + store the
 * preview; apply = plan again, refuse if the verdict changed ({@link ImportStaleException}), write.
 * Nothing touches the domain before apply.
 */
@Service
public class ImportService {

    private static final java.util.Set<ImportRecordType> MATCHABLE =
        java.util.Set.of(ImportRecordType.PLAYER, ImportRecordType.LEAGUE, ImportRecordType.TEAM);
    /** A whole federation's export fits one page, so the preview can show a tab at once. */
    private static final int MAX_PAGE_SIZE = 5000;
    private static final TypeReference<Map<String, FieldChange>> DIFF_TYPE = new TypeReference<>() { };
    private static final TypeReference<List<ImportIssue>> ISSUES_TYPE = new TypeReference<>() { };

    private final ImportSourceRegistry sources;
    private final ImportPlanner planner;
    private final ImportWriter writer;
    private final ImportRunRepository runRepository;
    private final ImportItemRepository itemRepository;
    private final FederationRepository federationRepository;
    private final ObjectMapper objectMapper;
    private final AnonymizationPolicy anonymizationPolicy;

    public ImportService(ImportSourceRegistry sources, ImportPlanner planner, ImportWriter writer,
                         ImportRunRepository runRepository, ImportItemRepository itemRepository,
                         FederationRepository federationRepository, ObjectMapper objectMapper,
                         @Value("${sportshub.importer.anonymized:allowed}") String anonymizationPolicy) {
        this.sources = sources;
        this.planner = planner;
        this.writer = writer;
        this.runRepository = runRepository;
        this.itemRepository = itemRepository;
        this.federationRepository = federationRepository;
        this.objectMapper = objectMapper;
        this.anonymizationPolicy = AnonymizationPolicy.valueOf(anonymizationPolicy.trim().toUpperCase());
    }

    public List<ImportSourceDto> sources() {
        return sources.all().stream().map(source -> new ImportSourceDto(source.key(), source.supports())).toList();
    }

    @Transactional
    public ImportRunDto preview(String sourceKey, String targetFederationId, InputStream file, String filename,
                                String actor) {
        federationRepository.findById(targetFederationId)
            .orElseThrow(() -> new FederationNotFoundException(targetFederationId));
        ImportBatch batch = sources.get(sourceKey).parse(file, filename);
        requireAllowed(batch.header());

        ImportRun run = new ImportRun();
        run.setSource(sourceKey);
        run.setInstance(batch.header().instance());
        run.setFilename(filename);
        run.setTargetFederationId(targetFederationId);
        run.setExportedAt(batch.header().exportedAt());
        run.setFormatVersion(batch.header().formatVersion());
        run.setAnonymized(batch.header().anonymized());
        run.setStatus(ImportRunStatus.PREVIEWED);
        run.setCreatedAt(Instant.now());
        run.setCreatedByDtfbId(actor);
        runRepository.save(run);

        store(run, planner.plan(batch, targetFederationId, Map.of()));
        return toDto(run);
    }

    @Transactional(readOnly = true)
    public List<ImportRunDto> runs() {
        return runRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ImportRunDto run(String runId) {
        return toDto(find(runId));
    }

    @Transactional(readOnly = true)
    public ImportItemPageDto items(String runId, ImportAction action, ImportRecordType recordType, int page, int size) {
        find(runId);
        Page<ImportItem> items = itemRepository.search(runId, action, recordType,
            PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE)));
        return new ImportItemPageDto(items.map(this::toDto).getContent(), items.getTotalElements());
    }

    /**
     * Assigns a record by hand, then plans the run again: a player record to an existing player (docs/28),
     * a league or team to an existing identity, or {@link ImportPlanner#OWN_IDENTITY} for one of its own
     * (docs/29). Null removes the assignment.
     */
    @Transactional
    public ImportRunDto match(String runId, String itemId, String playerId) {
        ImportRun run = requireOpen(runId);
        ImportItem item = itemRepository.findByIdAndRunId(itemId, runId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown import item " + itemId));
        if (!MATCHABLE.contains(item.getRecordType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only players, leagues and teams can be assigned");
        }
        List<ImportItem> items = itemRepository.findByRunIdOrderByPosition(runId);
        Map<String, String> matches = manualMatches(items);
        String key = matchKey(item.getRecordType(), item.getExternalId());
        if (playerId == null) {
            matches.remove(key);
        } else {
            matches.put(key, playerId);
        }
        List<PlannedItem> plan = planner.plan(batch(run, items), run.getTargetFederationId(), matches);
        itemRepository.deleteByRunId(runId);
        itemRepository.flush();
        store(run, plan);
        return toDto(run);
    }

    @Transactional
    public ImportRunDto apply(String runId, String actor) {
        ImportRun run = requireOpen(runId);
        List<ImportItem> items = itemRepository.findByRunIdOrderByPosition(runId);
        List<PlannedItem> plan = planner.plan(batch(run, items), run.getTargetFederationId(), manualMatches(items));
        if (!fingerprints(plan).equals(items.stream().map(this::fingerprint).toList())) {
            throw new ImportStaleException();
        }
        writer.write(run, plan, actor);
        run.setStatus(ImportRunStatus.APPLIED);
        run.setFinishedAt(Instant.now());
        run.setFinishedByDtfbId(actor);
        return toDto(runRepository.save(run));
    }

    /** Drops a preview; the run stays in the list as discarded, its records go. */
    @Transactional
    public ImportRunDto discard(String runId, String actor) {
        ImportRun run = requireOpen(runId);
        itemRepository.deleteByRunId(runId);
        run.setStatus(ImportRunStatus.DISCARDED);
        run.setFinishedAt(Instant.now());
        run.setFinishedByDtfbId(actor);
        return toDto(runRepository.save(run));
    }

    private void requireAllowed(ImportBatch.Header header) {
        if (anonymizationPolicy == AnonymizationPolicy.REQUIRED && !header.anonymized()) {
            throw new ImportAnonymizationException("IMPORT_ANONYMIZATION_REQUIRED",
                "This instance only accepts pseudonymized exports");
        }
        if (anonymizationPolicy == AnonymizationPolicy.FORBIDDEN && header.anonymized()) {
            throw new ImportAnonymizationException("IMPORT_ANONYMIZED_FORBIDDEN",
                "This instance does not accept pseudonymized exports");
        }
    }

    private void store(ImportRun run, List<PlannedItem> plan) {
        List<ImportItem> items = new ArrayList<>();
        for (int i = 0; i < plan.size(); i++) {
            PlannedItem planned = plan.get(i);
            ImportItem item = new ImportItem();
            item.setRun(run);
            item.setPosition(i);
            item.setRecordType(planned.recordType());
            item.setExternalId(planned.externalId());
            item.setLabel(planned.label());
            item.setAction(planned.action());
            item.setTargetEntityId(planned.targetEntityId());
            item.setManualMatchId(planned.manualMatchId());
            item.setLinkId(planned.linkId());
            item.setDiff(write(planned.diff()));
            item.setIssues(write(planned.issues()));
            item.setPayload(write(planned.payload()));
            items.add(item);
        }
        itemRepository.saveAll(items);
    }

    /** The source records again, from the stored payloads -- apply needs no file. */
    private ImportBatch batch(ImportRun run, List<ImportItem> items) {
        List<ImportedFederation> federations = new ArrayList<>();
        List<ImportedClub> clubs = new ArrayList<>();
        List<ImportedPlayer> players = new ArrayList<>();
        List<ImportedMembership> memberships = new ArrayList<>();
        List<ImportedSeason> seasons = new ArrayList<>();
        List<ImportedLeague> leagues = new ArrayList<>();
        List<ImportedTeam> teams = new ArrayList<>();
        List<ImportedRosterEntry> rosterEntries = new ArrayList<>();
        List<ImportedFixture> fixtures = new ArrayList<>();
        for (ImportItem item : items) {
            switch (item.getRecordType()) {
                case FEDERATION -> federations.add(read(item.getPayload(), ImportedFederation.class));
                case CLUB -> clubs.add(read(item.getPayload(), ImportedClub.class));
                case PLAYER -> players.add(read(item.getPayload(), ImportedPlayer.class));
                case CLUB_MEMBERSHIP -> memberships.add(read(item.getPayload(), ImportedMembership.class));
                case SEASON -> seasons.add(read(item.getPayload(), ImportedSeason.class));
                case LEAGUE -> leagues.add(read(item.getPayload(), ImportedLeague.class));
                case TEAM -> teams.add(read(item.getPayload(), ImportedTeam.class));
                case ROSTER_ENTRY -> rosterEntries.add(read(item.getPayload(), ImportedRosterEntry.class));
                case FIXTURE -> fixtures.add(read(item.getPayload(), ImportedFixture.class));
                default -> { }
            }
        }
        ImportBatch.Header header = new ImportBatch.Header(run.getSource(), run.getInstance(), run.getExportedAt(),
            run.getFormatVersion(), run.isAnonymized());
        return new ImportBatch(header, federations, clubs, players, memberships, seasons, leagues, teams, rosterEntries,
            fixtures);
    }

    private static Map<String, String> manualMatches(List<ImportItem> items) {
        Map<String, String> matches = new HashMap<>();
        items.stream().filter(item -> item.getManualMatchId() != null)
            .forEach(item -> matches.put(matchKey(item.getRecordType(), item.getExternalId()), item.getManualMatchId()));
        return matches;
    }

    /** Manual matches are keyed by record type too -- source ids repeat across types (club 10, team 10). */
    static String matchKey(ImportRecordType type, String externalId) {
        return type + ":" + externalId;
    }

    private List<String> fingerprints(List<PlannedItem> plan) {
        return plan.stream().map(PlannedItem::fingerprint).toList();
    }

    /** The stored item's fingerprint, built the same way as {@link PlannedItem#fingerprint()}. */
    private String fingerprint(ImportItem item) {
        return new PlannedItem(item.getRecordType(), item.getExternalId(), item.getLabel(), item.getAction(),
            item.getTargetEntityId(), item.getManualMatchId(), item.getLinkId(), read(item.getDiff(), DIFF_TYPE),
            read(item.getIssues(), ISSUES_TYPE), null).fingerprint();
    }

    private ImportRun find(String runId) {
        return runRepository.findById(runId).orElseThrow(() -> new ImportRunNotFoundException(runId));
    }

    private ImportRun requireOpen(String runId) {
        ImportRun run = find(runId);
        if (run.getStatus() != ImportRunStatus.PREVIEWED) {
            throw new ImportRunClosedException(runId);
        }
        return run;
    }

    private ImportRunDto toDto(ImportRun run) {
        List<ImportCountDto> counts = itemRepository.countByTypeAndAction(run.getId()).stream()
            .map(row -> new ImportCountDto((ImportRecordType) row[0], (ImportAction) row[1], (Long) row[2]))
            .toList();
        return new ImportRunDto(run.getId(), run.getSource(), run.getInstance(), run.getFilename(),
            run.getTargetFederationId(), run.getExportedAt(), run.isAnonymized(), run.getStatus(), run.getCreatedAt(),
            run.getCreatedByDtfbId(), run.getFinishedAt(), run.getFinishedByDtfbId(), counts);
    }

    private ImportItemDto toDto(ImportItem item) {
        return new ImportItemDto(item.getId(), item.getRecordType(), item.getExternalId(), item.getLabel(),
            item.getAction(), item.getTargetEntityId(), item.getManualMatchId(), item.getLinkId(), read(item.getDiff(), DIFF_TYPE),
            read(item.getIssues(), ISSUES_TYPE));
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize import item", e);
        }
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not read import item", e);
        }
    }

    private <T> T read(String json, TypeReference<T> type) {
        if (json == null) {
            return null;
        }
        try {
            return Objects.requireNonNull(objectMapper.readValue(json, type));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not read import item", e);
        }
    }
}
