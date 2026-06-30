package de.dtfb.sportshub.backend.importer.data;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ImportPlayer {
    private String name;
    private String playerNr;
    private Side side;
}
