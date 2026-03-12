package de.dtfb.sportshub.backend.importer.data;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ImportPayload {
    ImportMeta meta;
    List<ImportSeason> seasons;
}
