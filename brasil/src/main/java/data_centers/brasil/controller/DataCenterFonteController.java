package data_centers.brasil.controller;
import data_centers.brasil.dto.FonteResponse;
import data_centers.brasil.service.DataCenterFonteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.*;
@RestController @RequestMapping("/api/data-centers/{dataCenterId}/fontes") @RequiredArgsConstructor @Validated
public class DataCenterFonteController {
    private final DataCenterFonteService service;
    @GetMapping public Page<FonteResponse> list(@PathVariable @Positive Integer dataCenterId, @RequestParam org.springframework.util.MultiValueMap<String, String> filters, Pageable pageable) { return service.list(dataCenterId, filters, pageable); }
    @PutMapping("/{fonteId}") public ResponseEntity<Void> link(@PathVariable @Positive Integer dataCenterId, @PathVariable @Positive Integer fonteId) {
        service.link(dataCenterId, fonteId); return ResponseEntity.noContent().build();
    }
    @DeleteMapping("/{fonteId}") public ResponseEntity<Void> unlink(@PathVariable @Positive Integer dataCenterId, @PathVariable @Positive Integer fonteId) {
        service.unlink(dataCenterId, fonteId); return ResponseEntity.noContent().build();
    }
}
