package de.dtfb.sportshub.backend.teamparticipation;

/** Summary of what copy-forward cloned from the source season into the target. */
public record CopyForwardResultDto(
    int competitions,
    int disciplines,
    int stages,
    int pools,
    int participations
) {
}
