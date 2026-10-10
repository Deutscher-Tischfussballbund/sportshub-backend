package de.dtfb.sportshub.backend.maintenance;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Maintenance windows (SPO-119): the current one for everyone logged in, the list and editing for global admins. */
@RestController
public class MaintenanceNoticeController {

    private final MaintenanceNoticeService service;

    public MaintenanceNoticeController(MaintenanceNoticeService service) {
        this.service = service;
    }

    /** The notice to show right now; 204 when there is none. */
    @GetMapping("/v1/maintenance-notices/current")
    public ResponseEntity<MaintenanceNoticeDto> getCurrentMaintenanceNotice() {
        return service.current().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/v1/admin/maintenance-notices")
    @PreAuthorize("@authz.isAdmin()")
    public List<MaintenanceNoticeDto> getAllMaintenanceNotices() {
        return service.all();
    }

    @PostMapping("/v1/admin/maintenance-notices")
    @PreAuthorize("@authz.isAdmin()")
    public ResponseEntity<MaintenanceNoticeDto> createMaintenanceNotice(@RequestBody MaintenanceNoticeDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PutMapping("/v1/admin/maintenance-notices/{id}")
    @PreAuthorize("@authz.isAdmin()")
    public MaintenanceNoticeDto updateMaintenanceNotice(@PathVariable String id, @RequestBody MaintenanceNoticeDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/v1/admin/maintenance-notices/{id}")
    @PreAuthorize("@authz.isAdmin()")
    public void deleteMaintenanceNotice(@PathVariable String id) {
        service.delete(id);
    }
}
