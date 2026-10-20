package data_centers.brasil.controller;
import data_centers.brasil.dto.*;
import data_centers.brasil.service.DataCenterService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import java.net.URI;
@RestController @RequestMapping("/api/data-centers") @RequiredArgsConstructor @Validated
public class DataCenterController {
    private final DataCenterService service;
    @GetMapping public Page<DataCenterResponse> list(@RequestParam org.springframework.util.MultiValueMap<String, String> filters, Pageable pageable) { return service.list(filters, pageable); }
    @GetMapping("/{id}") public DataCenterResponse get(@PathVariable @Positive Integer id) { return service.get(id); }
    @PostMapping public ResponseEntity<DataCenterResponse> create(@RequestBody @Valid DataCenterRequest request) {
        var response = service.create(request);
        return ResponseEntity.created(URI.create("/api/data-centers/" + response.id())).body(response);
    }
    @PutMapping("/{id}") public DataCenterResponse update(@PathVariable @Positive Integer id, @RequestBody @Valid DataCenterRequest request) { return service.update(id, request); }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable @Positive Integer id) { service.delete(id); return ResponseEntity.noContent().build(); }
}
