package de.dtfb.sportshub.backend.releasenotes;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class ReleaseNoteDto {
    private String id;
    private String titleDe;
    private String titleEn;
    private String bodyDe;
    private String bodyEn;
    private Instant publishedAt;
}
