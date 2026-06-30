package de.dtfb.sportshub.backend.importer;

import lombok.Getter;
import lombok.Setter;

@Getter
public class ImportResult {
    private int success;
    @Setter
    private String message;

    public void incrementSuccess() {
        success++;
    }
}
