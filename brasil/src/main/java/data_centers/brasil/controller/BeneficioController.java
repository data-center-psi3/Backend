package data_centers.brasil.controller;
import data_centers.brasil.dto.*;
import data_centers.brasil.service.BeneficioService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import java.net.URI;
@RestController @RequestMapping("/api/beneficios") @RequiredArgsConstructor @Validated
public class BeneficioController {
    private final BeneficioService service;
    @GetMapping public Page<BeneficioResponse> list(@RequestParam org.springframework.util.MultiValueMap<String, String> filters, Pageable pageable) { return service.list(filters, pageable); }
    @GetMapping("/{id}") public BeneficioResponse get(@PathVariable @Positive Integer id) { return service.get(id); }
    @PostMapping public ResponseEntity<BeneficioResponse> create(@RequestBody @Valid BeneficioRequest request) {
        var response = service.create(request);
        return ResponseEntity.created(URI.create("/api/beneficios/" + response.id())).body(response);
    }
    @PutMapping("/{id}") public BeneficioResponse update(@PathVariable @Positive Integer id, @RequestBody @Valid BeneficioRequest request) { return service.update(id, request); }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable @Positive Integer id) { service.delete(id); return ResponseEntity.noContent().build(); }
}
