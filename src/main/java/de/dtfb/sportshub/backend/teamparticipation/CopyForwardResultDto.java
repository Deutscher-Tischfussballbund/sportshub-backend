package de.dtfb.sportshub.backend.teamparticipation;

/** Summary of what copy-forward cloned from the source season into the target. */
public record CopyForwardResultDto(
    int leagues,
    int tiers,
    int groups,
    int participations
) {
}
