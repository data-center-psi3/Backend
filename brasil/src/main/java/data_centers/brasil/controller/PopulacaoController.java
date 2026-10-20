package data_centers.brasil.controller;

import data_centers.brasil.dto.*;
import data_centers.brasil.service.PopulacaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/importacoes")
@RequiredArgsConstructor
public class PopulacaoController {
    private final PopulacaoService service;

    @GetMapping("/previa")
    public CargaPreviaResponse preview() { return service.previewClasspath(); }

    @PostMapping("/popular")
    public CargaResponse populate() { return service.populateClasspath(); }
}
