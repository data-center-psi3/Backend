package data_centers.brasil.controller;
import data_centers.brasil.dto.*;
import data_centers.brasil.service.ConteudoPesquisaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import java.net.URI;
@RestController @RequestMapping("/api/conteudos-pesquisa") @RequiredArgsConstructor @Validated
public class ConteudoPesquisaController {
    private final ConteudoPesquisaService service;
    @GetMapping public Page<ConteudoPesquisaResponse> list(@RequestParam org.springframework.util.MultiValueMap<String, String> filters, Pageable pageable) { return service.list(filters, pageable); }
    @GetMapping("/{id}") public ConteudoPesquisaResponse get(@PathVariable @Positive Integer id) { return service.get(id); }
    @PostMapping public ResponseEntity<ConteudoPesquisaResponse> create(@RequestBody @Valid ConteudoPesquisaRequest request) {
        var response = service.create(request);
        return ResponseEntity.created(URI.create("/api/conteudos-pesquisa/" + response.id())).body(response);
    }
    @PutMapping("/{id}") public ConteudoPesquisaResponse update(@PathVariable @Positive Integer id, @RequestBody @Valid ConteudoPesquisaRequest request) { return service.update(id, request); }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable @Positive Integer id) { service.delete(id); return ResponseEntity.noContent().build(); }
}
