package de.dtfb.sportshub.backend.matchday;

import org.springframework.context.ApplicationEvent;

public class MatchDayConfirmedEvent extends ApplicationEvent {
    private final MatchDay matchDay;

    public MatchDayConfirmedEvent(Object source, MatchDay matchDay) {
        super(source);
        this.matchDay = matchDay;
    }

    public MatchDay getMatchDay() {
        return matchDay;
    }
}
