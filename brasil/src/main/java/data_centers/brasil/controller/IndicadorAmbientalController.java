package data_centers.brasil.controller;
import data_centers.brasil.dto.*;
import data_centers.brasil.service.IndicadorAmbientalService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import java.net.URI;
@RestController @RequestMapping("/api/indicadores-ambientais") @RequiredArgsConstructor @Validated
public class IndicadorAmbientalController {
    private final IndicadorAmbientalService service;
    @GetMapping public Page<IndicadorAmbientalResponse> list(@RequestParam org.springframework.util.MultiValueMap<String, String> filters, Pageable pageable) { return service.list(filters, pageable); }
    @GetMapping("/{id}") public IndicadorAmbientalResponse get(@PathVariable @Positive Integer id) { return service.get(id); }
    @PostMapping public ResponseEntity<IndicadorAmbientalResponse> create(@RequestBody @Valid IndicadorAmbientalRequest request) {
        var response = service.create(request);
        return ResponseEntity.created(URI.create("/api/indicadores-ambientais/" + response.id())).body(response);
    }
    @PutMapping("/{id}") public IndicadorAmbientalResponse update(@PathVariable @Positive Integer id, @RequestBody @Valid IndicadorAmbientalRequest request) { return service.update(id, request); }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable @Positive Integer id) { service.delete(id); return ResponseEntity.noContent().build(); }
}
