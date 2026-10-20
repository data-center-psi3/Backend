package data_centers.brasil.controller;

import data_centers.brasil.dto.radar.RadarDTO.*;
import data_centers.brasil.service.RadarService;
import data_centers.brasil.service.RadarService.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/radar") @RequiredArgsConstructor
public class RadarController {
    private final RadarService service;
    // Binding único dos filtros compartilhados. Validação semântica no serviço.
    @GetMapping("/overview") public Metrics overview(@ModelAttribute Query query) { return service.overview(query); }
    @GetMapping("/states") public List<State> states(@ModelAttribute Query query,
        @RequestParam(defaultValue = "installations") String sortBy,
        @RequestParam(defaultValue = "desc") String direction) { return service.states(query, sortBy, direction); }
    @GetMapping("/states/{uf}") public StateDetail detail(@PathVariable String uf, @ModelAttribute Query query) {
        return service.detail(uf, query);
    }
    @GetMapping("/states/{uf}/cities") public List<City> cities(@PathVariable String uf, @ModelAttribute Query query,
        @RequestParam(defaultValue = "installations") String sortBy,
        @RequestParam(defaultValue = "desc") String direction) { return service.stateCities(uf, query, sortBy, direction); }
    @GetMapping("/operators/ranking") public List<Operator> operators(@ModelAttribute Query query,
        @RequestParam(defaultValue = "10") int limit) { return service.operatorRanking(query, limit); }
    @GetMapping("/map") public List<MapPoint> map(@ModelAttribute Query query) { return service.map(query); }
    @GetMapping("/cities/ranking") public List<City> cityRanking(@ModelAttribute Query query,
        @RequestParam(defaultValue = "10") int limit, @RequestParam(defaultValue = "installations") String sortBy,
        @RequestParam(defaultValue = "desc") String direction) { return service.cityRanking(query, limit, sortBy, direction); }
    @GetMapping("/filters") public Filters filters(@ModelAttribute Query query) { return service.filters(query); }
}
