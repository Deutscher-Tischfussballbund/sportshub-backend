package de.dtfb.sportshub.backend.importer;

import lombok.Getter;

@Getter
public class ImportResult {
    private int success;

    public void incrementSuccess() {
        success++;
    }
}
