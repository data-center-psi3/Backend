package data_centers.brasil.service;
import data_centers.brasil.filter.*;
import org.springframework.util.MultiValueMap;
import data_centers.brasil.dto.ImportacaoResponse;
import data_centers.brasil.model.DataCenterImportacao;
import data_centers.brasil.repository.DataCenterImportacaoRepository;
import org.apache.commons.csv.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
@Service @RequiredArgsConstructor
public class ImportacaoService {
    private final DataCenterImportacaoRepository repository;
    private final QueryFilterParser filterParser;
    private static final List<String> HEADERS = List.of("id", "operador", "unidade", "municipio", "uf", "codigo_ibge", "endereco", "latitude", "longitude", "precisao_geo", "tipo_instalacao", "status_operacional", "inicio_operacao", "potencia_ti_mw_unidade", "potencia_campus_mw", "area_m2_unidade", "redes_conectadas", "ix_conectados", "carriers", "voltada_ia", "resfriamento", "fonte_energia", "pue_declarado", "wue_declarado", "certificacoes", "robustez_evidencia", "fonte_url", "data_levantamento");
    @Transactional(readOnly = true)
    public Page<DataCenterImportacao> list(MultiValueMap<String, String> filters, Pageable pageable) {
        var conditions = filterParser.parse(filters, FilterResource.IMPORTACAO);
        return new PageImpl<>(repository.list(pageable.getPageSize(), pageable.getOffset(), conditions), pageable, repository.count(conditions));
    }
    @Transactional
    public ImportacaoResponse importCsv(MultipartFile file) {
        if (file.isEmpty()) throw new IllegalArgumentException("Arquivo vazio.");
        try (var reader = new PushbackReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8), 1)) {
            int first = reader.read();
            if (first != -1 && first != 0xFEFF) reader.unread(first);
            var format = CSVFormat.DEFAULT.builder().setDelimiter(';').setHeader().setSkipHeaderRecord(true).get();
            try (var parser = format.parse(reader)) {
                if (!parser.getHeaderNames().equals(HEADERS)) throw new IllegalArgumentException("Cabeçalho CSV inválido.");
                int count = 0;
                for (CSVRecord row : parser) {
                    if (!row.isConsistent()) throw new IllegalArgumentException("Linha CSV inválida.");
                    if (++count > 50000) throw new IllegalArgumentException("Máximo de 50000 linhas.");
                    repository.insert(new DataCenterImportacao(row.get("id"), row.get("operador"), row.get("unidade"), row.get("municipio"), row.get("uf"), row.get("codigo_ibge"), row.get("endereco"), row.get("latitude"), row.get("longitude"), row.get("precisao_geo"), row.get("tipo_instalacao"), row.get("status_operacional"), row.get("inicio_operacao"), row.get("potencia_ti_mw_unidade"), row.get("potencia_campus_mw"), row.get("area_m2_unidade"), row.get("redes_conectadas"), row.get("ix_conectados"), row.get("carriers"), row.get("voltada_ia"), row.get("resfriamento"), row.get("fonte_energia"), row.get("pue_declarado"), row.get("wue_declarado"), row.get("certificacoes"), row.get("robustez_evidencia"), row.get("fonte_url"), row.get("data_levantamento")));
                }
                return new ImportacaoResponse(count);
            }
        } catch (IOException | UncheckedIOException ex) {
            throw new IllegalArgumentException("Não foi possível ler o CSV.", ex);
        }
    }
}
