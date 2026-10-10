package de.dtfb.sportshub.backend.importer;

import de.dtfb.sportshub.backend.club.Club;
import de.dtfb.sportshub.backend.club.ClubRepository;
import de.dtfb.sportshub.backend.clubmembership.ClubMembership;
import de.dtfb.sportshub.backend.clubmembership.ClubMembershipRepository;
import de.dtfb.sportshub.backend.federation.Federation;
import de.dtfb.sportshub.backend.federation.FederationRepository;
import de.dtfb.sportshub.backend.history.EntityHistoryEntry;
import de.dtfb.sportshub.backend.history.EntityHistoryRepository;
import de.dtfb.sportshub.backend.history.HistoryEntityType;
import de.dtfb.sportshub.backend.player.Player;
import de.dtfb.sportshub.backend.player.PlayerNumber;
import de.dtfb.sportshub.backend.player.PlayerNumberRepository;
import de.dtfb.sportshub.backend.player.PlayerRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Decides, for every record of an {@link ImportBatch}, what applying it would do -- without writing
 * anything (docs/28). Shared by every source. Rules:
 * <ul>
 *   <li>Matching: {@link ExternalReference} first, then (players) any current or old player number,
 *       otherwise NEW. Name + birth year only raise {@link ImportIssueCode#DUPLICATE_SUSPECT}; the admin
 *       may assign the record by hand ({@code manualMatches}).</li>
 *   <li>Errors reject a record and every record depending on it; warnings are shown, the record is
 *       written. A missing birth year/gender is a warning: "complete to play, not complete to exist".</li>
 *   <li>A source value never clears a Sports Hub value (null in the source = unknown, not "remove").</li>
 *   <li>A field the source changes that was edited in the Sports Hub since the last import is a
 *       CONFLICT -- nothing of that record is written.</li>
 *   <li>Absence never deletes. Federations are only mapped, never created.</li>
 * </ul>
 */
@Component
public class ImportPlanner {

    /** Manual match value for a league or team that gets an identity of its own (docs/29). */
    public static final String OWN_IDENTITY = "own";
    /** {@code SS-NNNN}; the width isn't fixed, numbers may grow. */
    static final Pattern NUMBER_FORMAT = Pattern.compile("\\d{2}-\\d{4,}");
    private static final int OLDEST_PLAUSIBLE_BIRTH_YEAR = 1920;
    private static final int YOUNGEST_PLAUSIBLE_AGE = 5;

    private final ExternalReferenceRepository referenceRepository;
    private final FederationRepository federationRepository;
    private final ClubRepository clubRepository;
    private final PlayerRepository playerRepository;
    private final PlayerNumberRepository numberRepository;
    private final ClubMembershipRepository membershipRepository;
    private final EntityHistoryRepository historyRepository;
    private final HistoricalPlanner historicalPlanner;

    public ImportPlanner(ExternalReferenceRepository referenceRepository, FederationRepository federationRepository,
                         ClubRepository clubRepository, PlayerRepository playerRepository,
                         PlayerNumberRepository numberRepository, ClubMembershipRepository membershipRepository,
                         EntityHistoryRepository historyRepository, HistoricalPlanner historicalPlanner) {
        this.referenceRepository = referenceRepository;
        this.federationRepository = federationRepository;
        this.clubRepository = clubRepository;
        this.playerRepository = playerRepository;
        this.numberRepository = numberRepository;
        this.membershipRepository = membershipRepository;
        this.historyRepository = historyRepository;
        this.historicalPlanner = historicalPlanner;
    }


    @Transactional(readOnly = true)
    public List<PlannedItem> plan(ImportBatch batch, String targetFederationId, Map<String, String> manualMatches) {
        Context context = new Context(batch.header());
        List<PlannedItem> items = new ArrayList<>();
        Map<String, PlanTarget> federations = new HashMap<>();
        Map<String, PlanTarget> clubs = new HashMap<>();
        Map<String, PlanTarget> players = new HashMap<>();

        for (ImportedFederation federation : batch.federations()) {
            PlannedItem item = planFederation(federation, targetFederationId, context);
            items.add(item);
            federations.put(federation.externalId(), new PlanTarget(item.targetEntityId(), false));
        }
        for (ImportedClub club : batch.clubs()) {
            PlannedItem item = planClub(club, federations, targetFederationId, context);
            items.add(item);
            clubs.put(club.externalId(), target(item));
        }
        Set<String> duplicateNumbers = duplicates(batch.players().stream()
            .map(ImportedPlayer::number).filter(Objects::nonNull).toList());
        for (ImportedPlayer player : batch.players()) {
            PlannedItem item = planPlayer(player, duplicateNumbers, manualMatches, context);
            items.add(item);
            if (player.externalId() != null) {
                players.put(player.externalId(), target(item));
            }
        }
        Map<String, List<ClubMembership>> memberships = membershipRepository
            .findByPlayerIdIn(players.values().stream().map(PlanTarget::entityId).filter(Objects::nonNull).toList())
            .stream().collect(Collectors.groupingBy(m -> m.getPlayer().getId()));
        Map<String, String> labels = new HashMap<>();
        items.forEach(item -> labels.put(item.recordType() + ":" + item.externalId(), item.label()));
        for (ImportedMembership membership : batch.memberships()) {
            items.add(planMembership(membership, players, clubs, memberships, membershipLabel(membership, labels),
                labels, context));
        }
        items.addAll(historicalPlanner.plan(batch, clubs, players, manualMatches, context::reference));
        return items;
    }

    //region federations
    private PlannedItem planFederation(ImportedFederation source, String targetFederationId, Context context) {
        String label = source.name();
        if (source.externalId() == null) {
            return rejected(ImportRecordType.FEDERATION, null, label, source, ImportIssueCode.MISSING_SOURCE_ID);
        }
        ExternalReference reference = context.reference(ImportRecordType.FEDERATION, source.externalId());
        if (reference != null) {
            return item(ImportRecordType.FEDERATION, source.externalId(), label, ImportAction.UNCHANGED,
                reference.getEntityId(), null, Map.of(), List.of(), source);
        }
        Federation byName = source.name() == null ? null : context.federationsByName().get(normalize(source.name()));
        if (byName != null) {
            return item(ImportRecordType.FEDERATION, source.externalId(), label, ImportAction.NEW,
                byName.getId(), null, Map.of(), List.of(), source);
        }
        return item(ImportRecordType.FEDERATION, source.externalId(), label, ImportAction.NEW, targetFederationId,
            null, Map.of(), List.of(new ImportIssue(ImportIssueCode.FEDERATION_FALLBACK, targetFederationId)), source);
    }
    //endregion

    //region clubs
    private PlannedItem planClub(ImportedClub source, Map<String, PlanTarget> federations, String targetFederationId,
                                 Context context) {
        String label = source.name();
        if (source.externalId() == null) {
            return rejected(ImportRecordType.CLUB, null, label, source, ImportIssueCode.MISSING_SOURCE_ID);
        }
        if (isBlank(source.name())) {
            return rejected(ImportRecordType.CLUB, source.externalId(), label, source, ImportIssueCode.MISSING_NAME);
        }
        ExternalReference reference = context.reference(ImportRecordType.CLUB, source.externalId());
        Club club = reference == null ? null : context.clubs().get(reference.getEntityId());
        if (club == null) {
            List<ImportIssue> issues = new ArrayList<>();
            PlanTarget federation = source.federationExternalId() == null ? null
                : federations.get(source.federationExternalId());
            if (federation == null || federation.entityId() == null) {
                issues.add(new ImportIssue(ImportIssueCode.FEDERATION_FALLBACK, targetFederationId));
            }
            return item(ImportRecordType.CLUB, source.externalId(), label, ImportAction.NEW, null, null,
                Map.of(), issues, source);
        }
        Map<String, FieldChange> diff = new LinkedHashMap<>();
        track(diff, "name", club.getName(), source.name());
        track(diff, "shortName", club.getShortName(), source.shortName());
        track(diff, "city", club.getCity(), source.city());
        track(diff, "active", club.isActive(), source.active());
        return withConflicts(ImportRecordType.CLUB, source.externalId(), label, club.getId(), null, diff,
            new ArrayList<>(), source, HistoryEntityType.CLUB, reference);
    }
    //endregion

    //region players
    private PlannedItem planPlayer(ImportedPlayer source, Set<String> duplicateNumbers,
                                   Map<String, String> manualMatches, Context context) {
        String label = playerLabel(source);
        if (source.externalId() == null) {
            return rejected(ImportRecordType.PLAYER, null, label, source, ImportIssueCode.MISSING_SOURCE_ID);
        }
        if (isBlank(source.firstName()) || isBlank(source.lastName())) {
            return rejected(ImportRecordType.PLAYER, source.externalId(), label, source, ImportIssueCode.MISSING_NAME);
        }
        if (source.gender() == null && !isBlank(source.genderRaw())) {
            return rejected(ImportRecordType.PLAYER, source.externalId(), label, source,
                new ImportIssue(ImportIssueCode.UNKNOWN_GENDER, source.genderRaw()));
        }
        if (source.number() != null && duplicateNumbers.contains(source.number())) {
            return rejected(ImportRecordType.PLAYER, source.externalId(), label, source,
                new ImportIssue(ImportIssueCode.DUPLICATE_NUMBER_IN_EXPORT, source.number()));
        }

        List<ImportIssue> issues = new ArrayList<>();
        if (source.birthYear() == null) {
            issues.add(ImportIssue.of(ImportIssueCode.MISSING_BIRTH_YEAR));
        } else if (source.birthYear() < OLDEST_PLAUSIBLE_BIRTH_YEAR
            || source.birthYear() > Year.now().getValue() - YOUNGEST_PLAUSIBLE_AGE) {
            issues.add(new ImportIssue(ImportIssueCode.IMPLAUSIBLE_BIRTH_YEAR, source.birthYear().toString()));
        }
        if (source.gender() == null) {
            issues.add(ImportIssue.of(ImportIssueCode.MISSING_GENDER));
        }
        if (source.number() != null && !NUMBER_FORMAT.matcher(source.number()).matches()) {
            issues.add(new ImportIssue(ImportIssueCode.INVALID_NUMBER_FORMAT, source.number()));
        }

        ExternalReference reference = context.reference(ImportRecordType.PLAYER, source.externalId());
        PlayerNumber byNumber = source.number() == null ? null : context.numbers().get(source.number());
        String manualMatch = manualMatches.get(ImportService.matchKey(ImportRecordType.PLAYER, source.externalId()));

        Player player = null;
        if (manualMatch != null) {
            player = context.players().get(manualMatch);
            if (player != null) {
                issues.add(ImportIssue.of(ImportIssueCode.MATCHED_MANUALLY));
            }
        }
        if (player == null && reference != null) {
            player = context.players().get(reference.getEntityId());
        }
        if (player == null && byNumber != null) {
            player = byNumber.getPlayer();
            issues.add(ImportIssue.of(ImportIssueCode.MATCHED_BY_NUMBER));
        }
        String validManualMatch = player != null && player.getId().equals(manualMatch) ? manualMatch : null;

        if (player == null) {
            if (source.number() == null) {
                issues.add(ImportIssue.of(ImportIssueCode.NUMBER_WILL_BE_ISSUED));
            }
            String candidates = context.duplicateCandidates(source);
            if (!candidates.isEmpty()) {
                issues.add(new ImportIssue(ImportIssueCode.DUPLICATE_SUSPECT, candidates));
            }
            return item(ImportRecordType.PLAYER, source.externalId(), label, ImportAction.NEW, null, null,
                Map.of(), issues, source);
        }

        // The number names someone else -- or the matched player is already another record of this installation.
        boolean numberTaken = byNumber != null && !byNumber.getPlayer().getId().equals(player.getId());
        String linkedElsewhere = context.externalIdOf(ImportRecordType.PLAYER, player.getId());
        if (numberTaken || (linkedElsewhere != null && !linkedElsewhere.equals(source.externalId()))) {
            issues.add(new ImportIssue(ImportIssueCode.NUMBER_BELONGS_TO_OTHER_PLAYER,
                numberTaken ? byNumber.getPlayer().getId() : player.getId()));
            return item(ImportRecordType.PLAYER, source.externalId(), label, ImportAction.CONFLICT, player.getId(),
                validManualMatch, Map.of(), issues, source);
        }

        Map<String, FieldChange> diff = new LinkedHashMap<>();
        track(diff, "firstName", player.getFirstName(), source.firstName());
        track(diff, "lastName", player.getLastName(), source.lastName());
        track(diff, "birthYear", player.getBirthYear(), source.birthYear());
        track(diff, "gender", player.getGender(), source.gender());
        track(diff, "nationalLicense", player.getNationalLicense(), source.nationalLicense());
        track(diff, "internationalId", player.getInternationalId(), source.internationalId());
        track(diff, "number", player.getNationalId(), source.number());
        return withConflicts(ImportRecordType.PLAYER, source.externalId(), label, player.getId(), validManualMatch,
            diff, issues, source, HistoryEntityType.PLAYER, reference);
    }

    private static String playerLabel(ImportedPlayer source) {
        String name = (Objects.toString(source.firstName(), "") + " " + Objects.toString(source.lastName(), "")).trim();
        return source.number() == null ? name : name + " (" + source.number() + ")";
    }
    //endregion

    //region memberships
    private PlannedItem planMembership(ImportedMembership source, Map<String, PlanTarget> players,
                                       Map<String, PlanTarget> clubs, Map<String, List<ClubMembership>> memberships,
                                       String label, Map<String, String> labels, Context context) {
        PlanTarget player = resolve(source.playerExternalId(), ImportRecordType.PLAYER, players, context);
        PlanTarget club = resolve(source.clubExternalId(), ImportRecordType.CLUB, clubs, context);
        if (player == null) {
            return rejected(ImportRecordType.CLUB_MEMBERSHIP, source.externalId(), label, source,
                new ImportIssue(ImportIssueCode.UNKNOWN_PLAYER, source.playerExternalId()));
        }
        if (club == null) {
            return rejected(ImportRecordType.CLUB_MEMBERSHIP, source.externalId(), label, source,
                new ImportIssue(ImportIssueCode.UNKNOWN_CLUB, source.clubExternalId()));
        }
        if (player.rejected() || club.rejected()) {
            String blocking = player.rejected()
                ? labels.getOrDefault(ImportRecordType.PLAYER + ":" + source.playerExternalId(), source.playerExternalId())
                : labels.getOrDefault(ImportRecordType.CLUB + ":" + source.clubExternalId(), source.clubExternalId());
            return rejected(ImportRecordType.CLUB_MEMBERSHIP, source.externalId(), label, source,
                new ImportIssue(ImportIssueCode.BLOCKED_BY_REJECTED_RECORD, blocking));
        }
        if (player.entityId() == null || club.entityId() == null) {
            return item(ImportRecordType.CLUB_MEMBERSHIP, source.externalId(), label, ImportAction.NEW, null, null,
                Map.of(), List.of(), source);
        }
        List<ClubMembership> existing = memberships.getOrDefault(player.entityId(), List.of()).stream()
            .filter(m -> m.getClub().getId().equals(club.entityId())).toList();
        ClubMembership active = existing.stream().filter(m -> m.getLeftAt() == null).findFirst().orElse(null);
        if (active != null && source.left()) {
            return item(ImportRecordType.CLUB_MEMBERSHIP, source.externalId(), label, ImportAction.UPDATE,
                active.getId(), null, Map.of("left", new FieldChange("false", "true")), List.of(), source);
        }
        if (active != null || (source.left() && !existing.isEmpty())) {
            return item(ImportRecordType.CLUB_MEMBERSHIP, source.externalId(), label, ImportAction.UNCHANGED,
                active != null ? active.getId() : existing.getFirst().getId(), null, Map.of(), List.of(), source);
        }
        return item(ImportRecordType.CLUB_MEMBERSHIP, source.externalId(), label, ImportAction.NEW, null, null,
            Map.of(), List.of(), source);
    }

    /** "Player → Club" from the batch's own labels, falling back to the source ids. */
    private static String membershipLabel(ImportedMembership source, Map<String, String> labels) {
        String player = labels.getOrDefault(ImportRecordType.PLAYER + ":" + source.playerExternalId(),
            source.playerExternalId());
        String club = labels.getOrDefault(ImportRecordType.CLUB + ":" + source.clubExternalId(), source.clubExternalId());
        return player + " → " + club;
    }

    /** A record of this batch, or one an earlier run imported; null if neither. */
    private static PlanTarget resolve(String externalId, ImportRecordType type, Map<String, PlanTarget> inBatch,
                                  Context context) {
        if (externalId == null) {
            return null;
        }
        PlanTarget target = inBatch.get(externalId);
        if (target != null) {
            return target;
        }
        ExternalReference reference = context.reference(type, externalId);
        return reference == null ? null : new PlanTarget(reference.getEntityId(), false);
    }
    //endregion

    //region helpers
    /** UPDATE/UNCHANGED, or CONFLICT when a changed field was edited in the Sports Hub since the last import. */
    private PlannedItem withConflicts(ImportRecordType type, String externalId, String label, String entityId,
                                      String manualMatchId, Map<String, FieldChange> diff, List<ImportIssue> issues,
                                      Object payload, HistoryEntityType historyType, ExternalReference reference) {
        if (diff.isEmpty()) {
            return item(type, externalId, label, ImportAction.UNCHANGED, entityId, manualMatchId, diff, issues, payload);
        }
        if (reference != null) {
            Set<String> editedLocally = historyRepository
                .findByEntityTypeAndEntityIdAndChangedAtAfterOrderByChangedAtAsc(historyType, entityId,
                    reference.getLastImportedAt())
                .stream().map(EntityHistoryEntry::getFieldName).collect(Collectors.toSet());
            List<String> conflicting = diff.keySet().stream().filter(editedLocally::contains).sorted().toList();
            if (!conflicting.isEmpty()) {
                List<ImportIssue> withConflict = new ArrayList<>(issues);
                withConflict.add(new ImportIssue(ImportIssueCode.CHANGED_LOCALLY, String.join(",", conflicting)));
                return item(type, externalId, label, ImportAction.CONFLICT, entityId, manualMatchId, diff,
                    withConflict, payload);
            }
        }
        return item(type, externalId, label, ImportAction.UPDATE, entityId, manualMatchId, diff, issues, payload);
    }

    /** Records a change unless the source has no value -- a source never clears a Sports Hub value. */
    private static void track(Map<String, FieldChange> diff, String field, Object current, Object source) {
        if (source == null || Objects.equals(current, source)) {
            return;
        }
        diff.put(field, new FieldChange(current == null ? null : current.toString(), source.toString()));
    }

    /**
     * What dependent records (memberships) see. A record changed locally still is that entity; one
     * whose number names another player has no reliable identity, so its dependents are blocked too.
     */
    private static PlanTarget target(PlannedItem item) {
        return switch (item.action()) {
            case REJECTED -> PlanTarget.REJECTED;
            case CONFLICT -> item.hasIssue(ImportIssueCode.NUMBER_BELONGS_TO_OTHER_PLAYER)
                ? PlanTarget.REJECTED : new PlanTarget(item.targetEntityId(), false);
            case NEW -> PlanTarget.CREATED;
            default -> new PlanTarget(item.targetEntityId(), false);
        };
    }

    private static PlannedItem rejected(ImportRecordType type, String externalId, String label, Object payload,
                                        ImportIssueCode code) {
        return rejected(type, externalId, label, payload, ImportIssue.of(code));
    }

    private static PlannedItem rejected(ImportRecordType type, String externalId, String label, Object payload,
                                        ImportIssue issue) {
        return item(type, externalId == null ? "" : externalId, label, ImportAction.REJECTED, null, null, Map.of(),
            List.of(issue), payload);
    }

    private static PlannedItem item(ImportRecordType type, String externalId, String label, ImportAction action,
                                    String targetEntityId, String manualMatchId, Map<String, FieldChange> diff,
                                    List<ImportIssue> issues, Object payload) {
        return new PlannedItem(type, externalId, label, action, targetEntityId, manualMatchId, diff, issues, payload);
    }

    private static Set<String> duplicates(List<String> values) {
        Set<String> seen = new HashSet<>();
        return values.stream().filter(value -> !seen.add(value)).collect(Collectors.toSet());
    }

    static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Everything the planner looks up, loaded once per plan instead of per record. */
    private final class Context {
        private final ImportBatch.Header header;
        private Map<String, ExternalReference> references;
        private Map<String, String> externalIdsByEntity;
        private Map<String, Federation> federationsByName;
        private Map<String, Club> clubs;
        private Map<String, Player> players;
        private Map<String, List<Player>> playersByName;
        private Map<String, PlayerNumber> numbers;

        Context(ImportBatch.Header header) {
            this.header = header;
        }

        ExternalReference reference(ImportRecordType type, String externalId) {
            if (references == null) {
                references = new HashMap<>();
                externalIdsByEntity = new HashMap<>();
                for (ExternalReference reference : referenceRepository.findBySourceAndInstance(header.source(),
                    header.instance())) {
                    references.put(reference.getEntityType() + ":" + reference.getExternalId(), reference);
                    externalIdsByEntity.put(reference.getEntityType() + ":" + reference.getEntityId(),
                        reference.getExternalId());
                }
            }
            return references.get(type + ":" + externalId);
        }

        /** The source id of this installation already linked to the entity, if any. */
        String externalIdOf(ImportRecordType type, String entityId) {
            reference(type, "");
            return externalIdsByEntity.get(type + ":" + entityId);
        }

        Map<String, Federation> federationsByName() {
            if (federationsByName == null) {
                federationsByName = federationRepository.findAll().stream()
                    .filter(f -> f.getName() != null)
                    .collect(Collectors.toMap(f -> normalize(f.getName()), Function.identity(), (a, b) -> a));
            }
            return federationsByName;
        }

        Map<String, Club> clubs() {
            if (clubs == null) {
                clubs = clubRepository.findAll().stream().collect(Collectors.toMap(Club::getId, Function.identity()));
            }
            return clubs;
        }

        Map<String, Player> players() {
            if (players == null) {
                players = playerRepository.findAll().stream()
                    .collect(Collectors.toMap(Player::getId, Function.identity()));
                playersByName = players.values().stream()
                    .collect(Collectors.groupingBy(p -> normalize(p.getFirstName()) + "|" + normalize(p.getLastName())));
            }
            return players;
        }

        Map<String, PlayerNumber> numbers() {
            if (numbers == null) {
                numbers = numberRepository.findAll().stream()
                    .collect(Collectors.toMap(PlayerNumber::getNumber, Function.identity()));
            }
            return numbers;
        }

        /** Existing players with the same name and a compatible birth year, as comma-separated ids. */
        String duplicateCandidates(ImportedPlayer source) {
            players();
            return playersByName
                .getOrDefault(normalize(source.firstName()) + "|" + normalize(source.lastName()), List.of())
                .stream()
                .filter(p -> p.getBirthYear() == null || source.birthYear() == null
                    || p.getBirthYear().equals(source.birthYear()))
                .map(Player::getId)
                .sorted()
                .collect(Collectors.joining(","));
        }
    }
    //endregion
}
