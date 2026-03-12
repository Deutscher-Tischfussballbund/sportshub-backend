package de.dtfb.sportshub.backend.importer.data;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ImportMeta {
    private String organisation;
    //TODO mark the system where to store teams and players, e.g. local db, dtfb api, etc.
}
