package de.dtfb.sportshub.backend.exception;

import de.dtfb.sportshub.backend.location.LocationDeletionBlockedError;
import de.dtfb.sportshub.backend.location.LocationDeletionBlockedException;
import de.dtfb.sportshub.backend.club.ClubDeletionBlockedError;
import de.dtfb.sportshub.backend.club.ClubDeletionBlockedException;
import de.dtfb.sportshub.backend.group.GroupDeletionBlockedError;
import de.dtfb.sportshub.backend.importer.ImportAnonymizationException;
import de.dtfb.sportshub.backend.importer.ImportFormatException;
import de.dtfb.sportshub.backend.importer.ImportRunClosedException;
import de.dtfb.sportshub.backend.importer.ImportStaleException;
import de.dtfb.sportshub.backend.group.GroupDeletionBlockedException;
import de.dtfb.sportshub.backend.league.LeagueDeletionBlockedError;
import de.dtfb.sportshub.backend.league.LeagueDeletionBlockedException;
import de.dtfb.sportshub.backend.leaguerules.RuleSetDeletionBlockedError;
import de.dtfb.sportshub.backend.leaguerules.RuleSetDeletionBlockedException;
import de.dtfb.sportshub.backend.leaguerules.GamePlanLockedException;
import de.dtfb.sportshub.backend.leaguerules.RuleSetEditBlockedError;
import de.dtfb.sportshub.backend.leaguerules.RuleSetEditBlockedException;
import de.dtfb.sportshub.backend.leaguerules.RuleSetNameTakenError;
import de.dtfb.sportshub.backend.leaguerules.RuleSetNameTakenException;
import de.dtfb.sportshub.backend.category.CategoryShortNameTakenException;
import de.dtfb.sportshub.backend.roster.PlayerNotClubMemberError;
import de.dtfb.sportshub.backend.roster.PlayerNotClubMemberException;
import de.dtfb.sportshub.backend.roster.PlayerNotEligibleError;
import de.dtfb.sportshub.backend.roster.PlayerNotEligibleException;
import de.dtfb.sportshub.backend.roster.RosterSizeError;
import de.dtfb.sportshub.backend.roster.RosterSizeException;
import de.dtfb.sportshub.backend.season.SeasonDeletionBlockedError;
import de.dtfb.sportshub.backend.season.SeasonDeletionBlockedException;
import de.dtfb.sportshub.backend.teamparticipation.ParticipationDeletionBlockedError;
import de.dtfb.sportshub.backend.teamparticipation.ParticipationDeletionBlockedException;
import de.dtfb.sportshub.backend.teamparticipation.SeasonEndedError;
import de.dtfb.sportshub.backend.teamparticipation.SeasonEndedException;
import de.dtfb.sportshub.backend.tier.TierDeletionBlockedError;
import de.dtfb.sportshub.backend.tier.TierDeletionBlockedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.client.ClientAuthorizationRequiredException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundExceptionMarker.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError handleNotFound(NotFoundExceptionMarker ex) {
        return new ApiError(ex.getErrorCode(), ex.getMessage());
    }

    // Season delete refused because results exist → 409 with a structured body (what's attached).
    @ExceptionHandler(SeasonDeletionBlockedException.class)
    public ResponseEntity<SeasonDeletionBlockedError> handleSeasonDeletionBlocked(
        SeasonDeletionBlockedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new SeasonDeletionBlockedError("SEASON_HAS_RESULTS", ex.getMessage(), ex.getContents()));
    }

    // Roster addPlayer/submit refused by the resolved rule set's min/max roster size → structured
    // 409 (code + limit + current) so the frontend can show a precise, localized message.
    @ExceptionHandler(RosterSizeException.class)
    public ResponseEntity<RosterSizeError> handleRosterSize(RosterSizeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new RosterSizeError(ex.getCode(), ex.getMessage(), ex.getLimit(), ex.getCurrent()));
    }

    // Roster addPlayer refused because the player isn't an active member of the team's club yet →
    // 409, join the club first (see ClubMembershipService).
    @ExceptionHandler(PlayerNotClubMemberException.class)
    public ResponseEntity<PlayerNotClubMemberError> handlePlayerNotClubMember(PlayerNotClubMemberException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new PlayerNotClubMemberError("PLAYER_NOT_CLUB_MEMBER", ex.getMessage()));
    }

    // Category create/update would reuse another category's short name (case-insensitive) → 409.
    @ExceptionHandler(CategoryShortNameTakenException.class)
    public ResponseEntity<ApiError> handleCategoryShortNameTaken(CategoryShortNameTakenException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ApiError("CATEGORY_SHORT_NAME_TAKEN", ex.getMessage()));
    }

    // Roster addPlayer/submit refused because player(s) fail the league category's eligibility
    // profile (CategoryEligibility, docs/19) → 409 listing each failing player + reason.
    @ExceptionHandler(PlayerNotEligibleException.class)
    public ResponseEntity<PlayerNotEligibleError> handlePlayerNotEligible(PlayerNotEligibleException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new PlayerNotEligibleError("PLAYER_NOT_ELIGIBLE", ex.getMessage(), ex.getPlayers()));
    }

    // Registering a team into a league whose season has already ended → structured 409
    // (code + endDate) so the frontend can show a precise, localized message.
    @ExceptionHandler(SeasonEndedException.class)
    public ResponseEntity<SeasonEndedError> handleSeasonEnded(SeasonEndedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new SeasonEndedError("SEASON_ENDED", ex.getMessage(), ex.getEndDate()));
    }

    // Participation delete refused because the team has recorded matches/standings → 409, withdraw
    // instead (preserves the history the delete would have orphaned).
    @ExceptionHandler(ParticipationDeletionBlockedException.class)
    public ResponseEntity<ParticipationDeletionBlockedError> handleParticipationDeletionBlocked(
        ParticipationDeletionBlockedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ParticipationDeletionBlockedError("PARTICIPATION_HAS_MATCHES", ex.getMessage()));
    }

    // League delete refused because it still has a tier or a direct participation → 409, remove
    // the structure/participations first, bottom-up.
    @ExceptionHandler(LeagueDeletionBlockedException.class)
    public ResponseEntity<LeagueDeletionBlockedError> handleLeagueDeletionBlocked(
        LeagueDeletionBlockedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new LeagueDeletionBlockedError("LEAGUE_HAS_STRUCTURE_OR_PARTICIPATIONS", ex.getMessage()));
    }

    // Tier delete refused because it still has a group → 409, remove the groups first.
    @ExceptionHandler(TierDeletionBlockedException.class)
    public ResponseEntity<TierDeletionBlockedError> handleTierDeletionBlocked(
        TierDeletionBlockedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new TierDeletionBlockedError("TIER_HAS_GROUPS", ex.getMessage()));
    }

    // Group delete refused because a team is still placed in it → 409, move/unplace it first.
    @ExceptionHandler(GroupDeletionBlockedException.class)
    public ResponseEntity<GroupDeletionBlockedError> handleGroupDeletionBlocked(
        GroupDeletionBlockedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new GroupDeletionBlockedError("GROUP_HAS_PARTICIPATIONS", ex.getMessage()));
    }

    // Rule-set delete refused because a league/tier/federation still references it → 409,
    // detach the reference(s) first.
    @ExceptionHandler(RuleSetDeletionBlockedException.class)
    public ResponseEntity<RuleSetDeletionBlockedError> handleRuleSetDeletionBlocked(
        RuleSetDeletionBlockedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new RuleSetDeletionBlockedError("RULE_SET_IN_USE", ex.getMessage()));
    }

    // Club delete refused because it still has teams and/or active members -- deactivate instead.
    @ExceptionHandler(ClubDeletionBlockedException.class)
    public ResponseEntity<ClubDeletionBlockedError> handleClubDeletionBlocked(ClubDeletionBlockedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ClubDeletionBlockedError("CLUB_HAS_TEAMS_OR_MEMBERS", ex.getMessage()));
    }

    // Venue delete refused because fixtures are scheduled at it -- reassign or clear them first.
    @ExceptionHandler(LocationDeletionBlockedException.class)
    public ResponseEntity<LocationDeletionBlockedError> handleLocationDeletionBlocked(
        LocationDeletionBlockedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new LocationDeletionBlockedError("LOCATION_IN_USE", ex.getMessage(), ex.getFixtureCount()));
    }

    // Rule-set template name already taken by another template of the same owner (docs/21) -- the
    // body names that template so a client can offer to overwrite it.
    @ExceptionHandler(RuleSetNameTakenException.class)
    public ResponseEntity<RuleSetNameTakenError> handleRuleSetNameTaken(RuleSetNameTakenException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new RuleSetNameTakenError("RULE_SET_NAME_TAKEN", ex.getMessage(), ex.getExistingId()));
    }

    // Rule change refused because the owning season has ended -- its rules are frozen (docs/21).
    @ExceptionHandler(RuleSetEditBlockedException.class)
    public ResponseEntity<RuleSetEditBlockedError> handleRuleSetEditBlocked(RuleSetEditBlockedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new RuleSetEditBlockedError("RULE_SET_FROZEN", ex.getMessage()));
    }

    // Game plan change refused because a result has been entered -- its games are fixed (SPO-71).
    @ExceptionHandler(GamePlanLockedException.class)
    public ResponseEntity<RuleSetEditBlockedError> handleGamePlanLocked(GamePlanLockedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new RuleSetEditBlockedError("GAME_PLAN_LOCKED", ex.getMessage()));
    }

    // Import file not in the chosen source's format (docs/28) → 400 with the parser's message.
    @ExceptionHandler(ImportFormatException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleImportFormat(ImportFormatException ex) {
        return new ApiError("IMPORT_FORMAT", ex.getMessage());
    }

    // Export refused by this instance's anonymization policy (docs/28): a real export on a test
    // system, or a pseudonymized one in production → 400 with a code naming which.
    @ExceptionHandler(ImportAnonymizationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleImportAnonymization(ImportAnonymizationException ex) {
        return new ApiError(ex.getCode(), ex.getMessage());
    }

    // Applying an import whose preview no longer matches the data → 409, upload again (docs/28).
    @ExceptionHandler(ImportStaleException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError handleImportStale(ImportStaleException ex) {
        return new ApiError("IMPORT_STALE", ex.getMessage());
    }

    // Applying/discarding/assigning in an import run that's already applied or discarded → 409.
    @ExceptionHandler(ImportRunClosedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError handleImportRunClosed(ImportRunClosedException ex) {
        return new ApiError("IMPORT_RUN_CLOSED", ex.getMessage());
    }

    // Failsafe
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleBadRequest() {
        return new ApiError(
            "BAD_REQUEST",
            "The request is faulty");
    }

    // Controller/service explicitly chose a status (e.g. 400/409) via ResponseStatusException —
    // honour it instead of letting the catch-all below collapse it to 500.
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException ex) {
        HttpStatusCode status = ex.getStatusCode();
        String code = status instanceof HttpStatus httpStatus ? httpStatus.name() : "ERROR";
        return ResponseEntity.status(status).body(new ApiError(code, ex.getReason()));
    }

    // No route matches the path → 404. Without this the catch-all below turned a wrong URL (e.g. a
    // frontend calling a path the running backend doesn't have yet) into a misleading 500 (SPO-66).
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError handleNoResource(NoResourceFoundException ex) {
        return new ApiError("NOT_FOUND", "No endpoint for " + ex.getHttpMethod() + " /" + ex.getResourcePath());
    }

    // The path exists but not for this HTTP method → 405, same catch-all problem as above (SPO-66).
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ApiError handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return new ApiError("METHOD_NOT_ALLOWED", "Method " + ex.getMethod() + " is not supported here");
    }

    // Authorization denial (@PreAuthorize / method security) → 403, not the catch-all 500 below.
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiError handleAccessDenied() {
        return new ApiError(
            "FORBIDDEN",
            "You are not allowed to perform this action");
    }

    // @RegisteredOAuth2AuthorizedClient (tracker convert) resolves its argument BEFORE method
    // security runs, so an anonymous/non-GitHub-logged-in request never reaches @PreAuthorize at
    // all — it fails here instead. Map it to the same 403 that isAuthenticated() would have given.
    @ExceptionHandler(ClientAuthorizationRequiredException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiError handleClientAuthorizationRequired() {
        return new ApiError(
            "FORBIDDEN",
            "You are not allowed to perform this action");
    }

    // Failsafe
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiError handleUnexpected() {
        return new ApiError(
            "INTERNAL_ERROR",
            "An unexpected error occurred");
    }
}
