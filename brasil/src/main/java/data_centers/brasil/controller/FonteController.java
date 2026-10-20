package data_centers.brasil.controller;
import data_centers.brasil.dto.*;
import data_centers.brasil.service.FonteService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import java.net.URI;
@RestController @RequestMapping("/api/fontes") @RequiredArgsConstructor @Validated
public class FonteController {
    private final FonteService service;
    @GetMapping public Page<FonteResponse> list(@RequestParam org.springframework.util.MultiValueMap<String, String> filters, Pageable pageable) { return service.list(filters, pageable); }
    @GetMapping("/{id}") public FonteResponse get(@PathVariable @Positive Integer id) { return service.get(id); }
    @PostMapping public ResponseEntity<FonteResponse> create(@RequestBody @Valid FonteRequest request) {
        var response = service.create(request);
        return ResponseEntity.created(URI.create("/api/fontes/" + response.id())).body(response);
    }
    @PutMapping("/{id}") public FonteResponse update(@PathVariable @Positive Integer id, @RequestBody @Valid FonteRequest request) { return service.update(id, request); }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable @Positive Integer id) { service.delete(id); return ResponseEntity.noContent().build(); }
}
