package data_centers.brasil.controller;
import data_centers.brasil.dto.*;
import data_centers.brasil.service.ConsultaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.*;
import java.util.List;
@RestController @RequestMapping("/api") @RequiredArgsConstructor
public class ConsultaController {
    private final ConsultaService service;
    @GetMapping("/data-centers/filtro")
    public Page<DataCenterResponse> filter(@RequestParam org.springframework.util.MultiValueMap<String, String> filters, Pageable pageable) {
        return service.filter(filters, pageable);
    }
    @GetMapping("/mapa")
    public Page<MapaResponse> mapa(@RequestParam org.springframework.util.MultiValueMap<String, String> filters, Pageable pageable) { return service.mapa(filters, pageable); }
    @GetMapping("/dashboard") public List<DashboardResponse> dashboard(@RequestParam org.springframework.util.MultiValueMap<String, String> filters) { return service.dashboard(filters); }
}
