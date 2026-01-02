package de.dtfb.sportshub.backend.match;

public enum MatchState {
    BYE, // Match is a bye (e.g. in elimination stage)
    INCOMPLETE, // Match is waiting for entries to be assigned
    OPEN, // Match is waiting to be announced
    PAUSED, // Match is disabled (on pause)
    PLANNED, // Match is planned / awaiting announcement
    PLAYED, // Match is finished / played
    RUNNING, // Match is running
    SKIPPED // Match is skipped (e.g. Entry left tournament)
}
