package de.dtfb.sportshub.backend.importer;

import de.dtfb.sportshub.backend.club.Club;
import de.dtfb.sportshub.backend.club.ClubNotFoundException;
import de.dtfb.sportshub.backend.club.ClubRepository;
import de.dtfb.sportshub.backend.clubmembership.ClubMembership;
import de.dtfb.sportshub.backend.clubmembership.ClubMembershipRepository;
import de.dtfb.sportshub.backend.history.ChangeSet;
import de.dtfb.sportshub.backend.history.EntityHistoryService;
import de.dtfb.sportshub.backend.history.HistoryEntityType;
import de.dtfb.sportshub.backend.player.Player;
import de.dtfb.sportshub.backend.player.PlayerNotFoundException;
import de.dtfb.sportshub.backend.player.PlayerNumberKind;
import de.dtfb.sportshub.backend.player.PlayerNumberService;
import de.dtfb.sportshub.backend.player.PlayerRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes a checked plan (docs/28). Runs inside {@link ImportService#apply}'s transaction. Field changes
 * go through {@link ChangeSet}/{@link EntityHistoryService} like a manual edit, stamped with the admin
 * who applied the run; every written or confirmed record gets its {@link ExternalReference}.
 */
@Component
class ImportWriter {

    private final ExternalReferenceRepository referenceRepository;
    private final ClubRepository clubRepository;
    private final PlayerRepository playerRepository;
    private final PlayerNumberService numberService;
    private final ClubMembershipRepository membershipRepository;
    private final EntityHistoryService historyService;
    private final HistoricalImportWriter historicalWriter;

    ImportWriter(ExternalReferenceRepository referenceRepository, ClubRepository clubRepository,
                 PlayerRepository playerRepository, PlayerNumberService numberService,
                 ClubMembershipRepository membershipRepository, EntityHistoryService historyService,
                 HistoricalImportWriter historicalWriter) {
        this.referenceRepository = referenceRepository;
        this.clubRepository = clubRepository;
        this.playerRepository = playerRepository;
        this.numberService = numberService;
        this.membershipRepository = membershipRepository;
        this.historyService = historyService;
        this.historicalWriter = historicalWriter;
    }

    void write(ImportRun run, List<PlannedItem> plan, String actor) {
        Writing writing = new Writing(run, actor);
        HistoricalImportWriter.Apply history = historicalWriter.start(run, plan, writing);
        for (PlannedItem item : plan) {
            switch (item.recordType()) {
                case FEDERATION -> writing.federation(item);
                case CLUB -> writing.club(item);
                case PLAYER -> writing.player(item);
                case CLUB_MEMBERSHIP -> writing.membership(item);
                default -> history.write(item);
            }
        }
        history.finish();
    }

    /** One apply: the ids records got so far, by source id. */
    private final class Writing implements WrittenIds {
        private final ImportRun run;
        private final String actor;
        private final Map<String, ExternalReference> references = new HashMap<>();
        private final Map<String, String> federationIds = new HashMap<>();
        private final Map<String, String> clubIds = new HashMap<>();
        private final Map<String, String> playerIds = new HashMap<>();

        Writing(ImportRun run, String actor) {
            this.run = run;
            this.actor = actor;
            referenceRepository.findBySourceAndInstance(run.getSource(), run.getInstance())
                .forEach(reference -> references.put(key(reference.getEntityType(), reference.getExternalId()), reference));
        }

        void federation(PlannedItem item) {
            if (item.action() == ImportAction.REJECTED) {
                return;
            }
            federationIds.put(item.externalId(), item.targetEntityId());
            // A fallback to the run's federation is not a mapping -- the next run may find a better one.
            if (!item.hasIssue(ImportIssueCode.FEDERATION_FALLBACK)) {
                link(ImportRecordType.FEDERATION, item.externalId(), item.targetEntityId());
            }
        }

        void club(PlannedItem item) {
            ImportedClub source = (ImportedClub) item.payload();
            switch (item.action()) {
                case NEW -> {
                    Club club = new Club();
                    club.setName(source.name());
                    club.setShortName(source.shortName());
                    club.setCity(source.city());
                    club.setActive(source.active());
                    String federationId = source.federationExternalId() == null ? null
                        : federationIds.get(source.federationExternalId());
                    club.setFederationId(federationId != null ? federationId : run.getTargetFederationId());
                    clubRepository.save(club);
                    clubIds.put(item.externalId(), club.getId());
                    link(ImportRecordType.CLUB, item.externalId(), club.getId());
                }
                case UPDATE -> {
                    Club club = clubRepository.findById(item.targetEntityId())
                        .orElseThrow(() -> new ClubNotFoundException(item.targetEntityId()));
                    ChangeSet changes = ChangeSet.forEntity(HistoryEntityType.CLUB, club.getId());
                    if (item.diff().containsKey("name")) {
                        changes.track("name", club.getName(), source.name());
                        club.setName(source.name());
                    }
                    if (item.diff().containsKey("shortName")) {
                        changes.track("shortName", club.getShortName(), source.shortName());
                        club.setShortName(source.shortName());
                    }
                    if (item.diff().containsKey("city")) {
                        changes.track("city", club.getCity(), source.city());
                        club.setCity(source.city());
                    }
                    if (item.diff().containsKey("active")) {
                        changes.track("active", club.isActive(), source.active());
                        club.setActive(source.active());
                    }
                    historyService.record(changes, actor);
                    clubRepository.save(club);
                    clubIds.put(item.externalId(), club.getId());
                    link(ImportRecordType.CLUB, item.externalId(), club.getId());
                }
                case UNCHANGED -> {
                    clubIds.put(item.externalId(), item.targetEntityId());
                    link(ImportRecordType.CLUB, item.externalId(), item.targetEntityId());
                }
                case CONFLICT -> clubIds.put(item.externalId(), item.targetEntityId());
                case REJECTED -> { }
            }
        }

        void player(PlannedItem item) {
            ImportedPlayer source = (ImportedPlayer) item.payload();
            switch (item.action()) {
                case NEW -> {
                    Player player = new Player();
                    player.setFirstName(source.firstName().trim());
                    player.setLastName(source.lastName().trim());
                    player.setBirthYear(source.birthYear());
                    player.setGender(source.gender());
                    player.setNationalLicense(source.nationalLicense());
                    player.setInternationalId(source.internationalId());
                    playerRepository.save(player);
                    if (source.number() != null) {
                        numberService.assign(player, source.number(), PlayerNumberService.kindOf(source.number()));
                    } else {
                        numberService.issue(player, PlayerNumberKind.NOT_GIVEN);
                    }
                    playerIds.put(item.externalId(), player.getId());
                    link(ImportRecordType.PLAYER, item.externalId(), player.getId());
                }
                case UPDATE -> {
                    Player player = playerRepository.findById(item.targetEntityId())
                        .orElseThrow(() -> new PlayerNotFoundException(item.targetEntityId()));
                    ChangeSet changes = ChangeSet.forEntity(HistoryEntityType.PLAYER, player.getId());
                    if (item.diff().containsKey("firstName")) {
                        changes.track("firstName", player.getFirstName(), source.firstName().trim());
                        player.setFirstName(source.firstName().trim());
                    }
                    if (item.diff().containsKey("lastName")) {
                        changes.track("lastName", player.getLastName(), source.lastName().trim());
                        player.setLastName(source.lastName().trim());
                    }
                    if (item.diff().containsKey("birthYear")) {
                        changes.track("birthYear", player.getBirthYear(), source.birthYear());
                        player.setBirthYear(source.birthYear());
                    }
                    if (item.diff().containsKey("gender")) {
                        changes.track("gender", player.getGender(), source.gender());
                        player.setGender(source.gender());
                    }
                    if (item.diff().containsKey("nationalLicense")) {
                        changes.track("nationalLicense", player.getNationalLicense(), source.nationalLicense());
                        player.setNationalLicense(source.nationalLicense());
                    }
                    if (item.diff().containsKey("internationalId")) {
                        player.setInternationalId(source.internationalId());
                    }
                    historyService.record(changes, actor);
                    playerRepository.save(player);
                    if (item.diff().containsKey("number")) {
                        numberService.assign(player, source.number(), PlayerNumberService.kindOf(source.number()));
                    }
                    playerIds.put(item.externalId(), player.getId());
                    link(ImportRecordType.PLAYER, item.externalId(), player.getId());
                }
                case UNCHANGED -> {
                    playerIds.put(item.externalId(), item.targetEntityId());
                    link(ImportRecordType.PLAYER, item.externalId(), item.targetEntityId());
                }
                case CONFLICT -> {
                    if (!item.hasIssue(ImportIssueCode.NUMBER_BELONGS_TO_OTHER_PLAYER)) {
                        playerIds.put(item.externalId(), item.targetEntityId());
                    }
                }
                case REJECTED -> { }
            }
        }

        void membership(PlannedItem item) {
            ImportedMembership source = (ImportedMembership) item.payload();
            switch (item.action()) {
                case NEW -> {
                    String playerId = resolve(ImportRecordType.PLAYER, source.playerExternalId(), playerIds);
                    String clubId = resolve(ImportRecordType.CLUB, source.clubExternalId(), clubIds);
                    if (playerId == null || clubId == null) {
                        return;
                    }
                    Instant now = Instant.now();
                    ClubMembership membership = new ClubMembership();
                    membership.setPlayer(playerRepository.getReferenceById(playerId));
                    membership.setClub(clubRepository.getReferenceById(clubId));
                    if (source.left()) {
                        Instant leftAt = source.leftAt() != null ? source.leftAt() : now;
                        membership.setJoinedAt(source.joinedAt() != null ? source.joinedAt() : leftAt);
                        membership.setLeftAt(leftAt);
                    } else {
                        membership.setJoinedAt(source.joinedAt() != null ? source.joinedAt() : now);
                    }
                    membershipRepository.save(membership);
                }
                case UPDATE -> membershipRepository.findById(item.targetEntityId()).ifPresent(membership -> {
                    membership.setLeftAt(source.leftAt() != null ? source.leftAt() : Instant.now());
                    membershipRepository.save(membership);
                });
                default -> { }
            }
        }

        private String resolve(ImportRecordType type, String externalId, Map<String, String> written) {
            String id = written.get(externalId);
            if (id != null) {
                return id;
            }
            ExternalReference reference = references.get(key(type, externalId));
            return reference == null ? null : reference.getEntityId();
        }

        @Override
        public String id(ImportRecordType type, String externalId) {
            if (externalId == null) return null;
            return switch (type) {
                case CLUB -> resolve(type, externalId, clubIds);
                case PLAYER -> resolve(type, externalId, playerIds);
                default -> {
                    ExternalReference reference = references.get(key(type, externalId));
                    yield reference == null ? null : reference.getEntityId();
                }
            };
        }

        @Override
        public void link(ImportRecordType type, String externalId, String entityId) {
            ExternalReference reference = references.computeIfAbsent(key(type, externalId), k -> {
                ExternalReference created = new ExternalReference();
                created.setSource(run.getSource());
                created.setInstance(run.getInstance());
                created.setEntityType(type);
                created.setExternalId(externalId);
                return created;
            });
            reference.setEntityId(entityId);
            // After this record's history entries, so they don't count as local edits next time.
            reference.setLastImportedAt(Instant.now());
            referenceRepository.save(reference);
        }

        private static String key(ImportRecordType type, String externalId) {
            return type + ":" + externalId;
        }
    }
}
