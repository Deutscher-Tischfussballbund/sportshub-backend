package de.dtfb.sportshub.backend.matchday;

/**
 * What the current user is for one fixture's result (docs/17): a neutral admin (league admin of the
 * fixture's league, admin of its federation, or global admin), a team member of either side (on the
 * team's current roster in that league, a captain, or a club/region admin above the team), and a
 * captain ({@code team_admin}) of either side.
 */
public record ResultActor(boolean neutralAdmin,
                          boolean homeMember, boolean awayMember,
                          boolean homeCaptain, boolean awayCaptain) {

    /** The one side the user acts for as a team member, or {@code null} if none or both (ambiguous). */
    public Side memberSide() {
        if (homeMember == awayMember) {
            return null;
        }
        return homeMember ? Side.HOME : Side.AWAY;
    }

    /** The one side the user is captain of, or {@code null} if none or both (ambiguous). */
    public Side captainSide() {
        if (homeCaptain == awayCaptain) {
            return null;
        }
        return homeCaptain ? Side.HOME : Side.AWAY;
    }

    public enum Side {
        HOME,
        AWAY
    }
}
