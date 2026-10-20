package data_centers.brasil.controller;
import data_centers.brasil.dto.ImportacaoResponse;
import data_centers.brasil.model.DataCenterImportacao;
import data_centers.brasil.service.ImportacaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.*;
@RestController @RequestMapping("/api/importacoes") @RequiredArgsConstructor
public class ImportacaoController {
    private final ImportacaoService service;
    @GetMapping public Page<DataCenterImportacao> list(@RequestParam org.springframework.util.MultiValueMap<String, String> filters, Pageable pageable) { return service.list(filters, pageable); }
    @PostMapping(consumes = "multipart/form-data")
    public ImportacaoResponse upload(@RequestParam("arquivo") MultipartFile arquivo) { return service.importCsv(arquivo); }
}
