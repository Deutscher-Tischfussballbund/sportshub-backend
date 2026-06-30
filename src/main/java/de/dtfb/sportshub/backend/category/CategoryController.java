package de.dtfb.sportshub.backend.category;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/v1/category")
public class CategoryController {

    private final CategoryService service;

    public CategoryController(CategoryService service) {
        this.service = service;
    }

    @GetMapping
    public List<CategoryDto> getAll() {
        return service.getAll();
    }

    @PostMapping
    @PreAuthorize("@authz.isAdmin()")
    public ResponseEntity<CategoryDto> create(@RequestBody CategoryDto categoryDto) {
        CategoryDto returnedDto = service.create(categoryDto);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/" + returnedDto.getId()).build().toUri();

        return ResponseEntity.created(location).body(returnedDto);
    }

    @GetMapping("/{id}")
    public CategoryDto get(@PathVariable String id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.isAdmin()")
    public CategoryDto update(@PathVariable String id, @RequestBody CategoryDto categoryDto) {
        return service.update(id, categoryDto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.isAdmin()")
    public void delete(@PathVariable String id) {
        service.delete(id);
    }
}
