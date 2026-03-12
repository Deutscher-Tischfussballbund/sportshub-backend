package de.dtfb.sportshub.backend.importer.data;

import de.dtfb.sportshub.backend.enums.Side;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ImportPlayer {
    private String name;
    private String id;
    private Side side;
}
