package de.dtfb.sportshub.backend.access.apikey;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Global-admin management of read-only API keys (docs/20-api-keys.md) -- reads included, since the
 * list reveals which machine consumers exist.
 */
@RestController
@RequestMapping("/v1/admin/api-keys")
public class ApiKeyController {

    private final ApiKeyService service;

    public ApiKeyController(ApiKeyService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@authz.isAdmin()")
    public List<ApiKeyDto> getAllApiKeys() {
        return service.getAll();
    }

    /** The response is the only time the plaintext key is returned -- it can't be retrieved later. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authz.isAdmin()")
    public ApiKeyCreatedDto createApiKey(@RequestBody ApiKeyDto dto, @AuthenticationPrincipal Jwt jwt) {
        return service.create(dto, jwt.getClaimAsString("dtfb_id"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.isAdmin()")
    public ApiKeyDto updateApiKey(@PathVariable String id, @RequestBody ApiKeyDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.isAdmin()")
    public void deleteApiKey(@PathVariable String id) {
        service.delete(id);
    }
}
