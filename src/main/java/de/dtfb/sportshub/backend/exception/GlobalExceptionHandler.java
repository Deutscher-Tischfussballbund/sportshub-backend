package de.dtfb.sportshub.backend.exception;

import de.dtfb.sportshub.backend.roster.RosterSizeError;
import de.dtfb.sportshub.backend.roster.RosterSizeException;
import de.dtfb.sportshub.backend.season.SeasonDeletionBlockedError;
import de.dtfb.sportshub.backend.season.SeasonDeletionBlockedException;
import de.dtfb.sportshub.backend.teamparticipation.ParticipationDeletionBlockedError;
import de.dtfb.sportshub.backend.teamparticipation.ParticipationDeletionBlockedException;
import de.dtfb.sportshub.backend.teamparticipation.SeasonEndedError;
import de.dtfb.sportshub.backend.teamparticipation.SeasonEndedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

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

    // Authorization denial (@PreAuthorize / method security) → 403, not the catch-all 500 below.
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiError handleAccessDenied() {
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
