package de.dtfb.sportshub.backend.importer;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** The importer (docs/28): upload a source file for a preview, then apply or discard it. Global admins only. */
@RestController
@PreAuthorize("@authz.isAdmin()")
public class ImportController {

    private final ImportService importService;
    private final ImportUndoService undoService;

    public ImportController(ImportService importService, ImportUndoService undoService) {
        this.importService = importService;
        this.undoService = undoService;
    }

    @GetMapping("/v1/admin/imports/sources")
    public List<ImportSourceDto> sources() {
        return importService.sources();
    }

    @GetMapping("/v1/admin/imports")
    public List<ImportRunDto> runs() {
        return importService.runs();
    }

    @PostMapping(value = "/v1/admin/imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportRunDto upload(@RequestParam String source, @RequestParam String targetFederationId,
                               @RequestPart("file") MultipartFile file, @AuthenticationPrincipal Jwt jwt)
        throws IOException {
        try (InputStream in = file.getInputStream()) {
            return importService.preview(source, targetFederationId, in, file.getOriginalFilename(), dtfbId(jwt));
        }
    }

    @GetMapping("/v1/admin/imports/{runId}")
    public ImportRunDto run(@PathVariable String runId) {
        return importService.run(runId);
    }

    @GetMapping("/v1/admin/imports/{runId}/items")
    public ImportItemPageDto items(@PathVariable String runId,
                                   @RequestParam(required = false) ImportAction action,
                                   @RequestParam(required = false) ImportRecordType recordType,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "50") int size) {
        return importService.items(runId, action, recordType, page, size);
    }

    @PutMapping("/v1/admin/imports/{runId}/items/{itemId}/match")
    public ImportRunDto match(@PathVariable String runId, @PathVariable String itemId,
                              @RequestBody ManualMatchRequest request) {
        return importService.match(runId, itemId, request.playerId());
    }

    @PostMapping("/v1/admin/imports/{runId}/apply")
    public ImportRunDto apply(@PathVariable String runId, @AuthenticationPrincipal Jwt jwt) {
        return importService.apply(runId, dtfbId(jwt));
    }

    /** What undoing an applied run would do, or why it can't (docs/28). */
    @GetMapping("/v1/admin/imports/{runId}/undo")
    public UndoCheckDto undoCheck(@PathVariable String runId) {
        return undoService.check(runId);
    }

    @PostMapping("/v1/admin/imports/{runId}/undo")
    public ImportRunDto undo(@PathVariable String runId, @AuthenticationPrincipal Jwt jwt) {
        return undoService.undo(runId, dtfbId(jwt));
    }

    @DeleteMapping("/v1/admin/imports/{runId}")
    public ImportRunDto discard(@PathVariable String runId, @AuthenticationPrincipal Jwt jwt) {
        return importService.discard(runId, dtfbId(jwt));
    }

    private static String dtfbId(Jwt jwt) {
        return jwt == null ? null : jwt.getClaimAsString("dtfb_id");
    }
}
