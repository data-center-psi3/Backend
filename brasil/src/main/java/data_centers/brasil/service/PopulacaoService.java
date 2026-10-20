package data_centers.brasil.service;

import data_centers.brasil.dto.*;
import data_centers.brasil.exception.*;
import data_centers.brasil.importacao.*;
import data_centers.brasil.model.*;
import data_centers.brasil.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PopulacaoService {
    public static final String CSV_PATH = "data/instituto-lovelace-data-centers-brasil-2026-09-22.csv";
    public static final String INSTITUICAO = "Instituto Lovelace (levantamento CSV)";
    private final CsvDataCenterReader reader;
    private final DataCenterCsvNormalizer normalizer;
    private final DataCenterService dataCenterService;
    private final DataCenterRepository dataCenters;
    private final FonteRepository fontes;
    private final DataCenterFonteRepository vinculos;
    private final IndicadorAmbientalRepository indicadores;
    private final ConteudoPesquisaRepository conteudos;
    private final DataCenterImportacaoRepository importacoes;
    private final CargaLockRepository lock;

    public CargaPreviaResponse previewClasspath() {
        var normalized = normalize(reader.read(openResource()));
        return new CargaPreviaResponse(CSV_PATH, normalized.size(),
            normalized.stream().filter(r -> r.dataCenter().latitude() != null && r.dataCenter().longitude() != null).count(),
            normalized.stream().filter(r -> r.dataCenter().potenciaMw() != null).count(),
            normalized.stream().limit(5).map(RegistroNormalizado::dataCenter).toList(),
            normalized.stream().flatMap(r -> r.avisos().stream()).toList());
    }

    @Transactional
    public CargaResponse populateClasspath() { return populateRecords(reader.read(openResource())); }

    /** Mesmo pipeline para testes e futuras origens CSV, sem depender de um caminho no disco. */
    @Transactional
    public CargaResponse populate(InputStream stream) { return populateRecords(reader.read(stream)); }

    private InputStream openResource() {
        try { return new ClassPathResource(CSV_PATH).getInputStream(); }
        catch (IOException ex) { throw new CsvImportException("CSV não encontrado em resources/" + CSV_PATH, ex); }
    }

    private List<RegistroNormalizado> normalize(List<DataCenterImportacao> rows) {
        var seen = new HashSet<String>();
        var result = new ArrayList<RegistroNormalizado>();
        for (var row : rows) {
            var normalized = normalizer.normalize(row);
            if (!seen.add(row.idOriginal().strip())) {
                throw new CsvImportException("Identificador repetido no CSV: " + row.idOriginal());
            }
            result.add(normalized);
        }
        return List.copyOf(result);
    }

    private CargaResponse populateRecords(List<DataCenterImportacao> rows) {
        var records = normalize(rows); // Todo o arquivo é validado antes de gravar.
        lock.acquire();
        int created = 0, reused = 0, sources = 0, metrics = 0, research = 0, raw = 0, skipped = 0;
        var avisos = new ArrayList<CargaAviso>();
        for (var record : records) {
            avisos.addAll(record.avisos());
            var row = record.original();
            String sourceTitle = "Levantamento CSV: " + row.idOriginal().strip();
            var existingSources = fontes.findByInstituicaoAndTitulo(INSTITUICAO, sourceTitle);
            if (existingSources.size() > 1) {
                throw new ResourceConflictException("Mais de uma fonte de carga para " + row.idOriginal() + ". Revise os registros.");
            }
            Fonte source = existingSources.isEmpty() ? null : existingSources.getFirst();
            if (source != null) {
                var links = vinculos.findByIdFonteId(source.getId());
                if (links.size() > 1) throw new ResourceConflictException("Fonte de carga ligada a vários data centers: " + row.idOriginal());
                if (!links.isEmpty()) { skipped++; continue; }
            }

            var request = record.dataCenter();
            var matches = dataCenters.findByNomeAndEmpresaAndCidadeAndEstado(
                request.nome(), request.empresa(), request.cidade(), request.estado());
            if (matches.size() > 1) {
                throw new ResourceConflictException("Data center ambíguo para " + row.idOriginal() + ": mais de um cadastro com os mesmos nome, empresa, cidade e estado.");
            }
            DataCenter dc;
            if (matches.isEmpty()) {
                int id = dataCenterService.create(request).id();
                dc = dataCenters.findById(id).orElseThrow();
                created++;
            } else {
                dc = matches.getFirst();
                reused++; // Mantém dados já editados; apenas acrescenta a proveniência.
            }

            if (source == null) {
                source = new Fonte();
                source.setInstituicao(INSTITUICAO);
                source.setTitulo(sourceTitle);
                source.setUrl(normalizer.text(row.fonteUrl()));
                source.setDataConsulta(LocalDate.now());
                source = fontes.save(source);
                sources++;
            }
            vinculos.save(new DataCenterFonte(dc, source));

            if (!importacoes.existsByIdOriginal(row.idOriginal())) {
                importacoes.insert(row);
                raw++;
            }
            metrics += metric(dc, source, record, "PUE", row.pueDeclarado(), "índice");
            metrics += metric(dc, source, record, "WUE", row.wueDeclarado(), "L/kWh");

            var content = new ConteudoPesquisa();
            content.setDataCenter(dc);
            content.setFonte(source);
            content.setTitulo(sourceTitle);
            content.setCategoria("LEVANTAMENTO_CSV");
            content.setConteudo(record.conteudoPesquisa());
            // Data do levantamento não é tratada como data de publicação ou validação.
            conteudos.save(content);
            research++;
        }
        dataCenters.flush();
        return new CargaResponse(CSV_PATH, records.size(), created, reused, sources,
            metrics, research, raw, skipped, List.copyOf(avisos));
    }

    private int metric(DataCenter dc, Fonte source, RegistroNormalizado record, String type, String raw, String unit) {
        if (normalizer.text(raw) == null) return 0;
        var metric = new IndicadorAmbiental();
        metric.setDataCenter(dc);
        metric.setFonte(source);
        metric.setTipo(type);
        metric.setValor(normalizer.number(record.original().idOriginal(), type.toLowerCase(Locale.ROOT) + "_declarado", raw, 4, new ArrayList<>()));
        metric.setUnidade(unit);
        metric.setPeriodo(record.dataLevantamento() == null ? null : record.dataLevantamento().toString());
        metric.setObservacoes("Declaração original do CSV: " + raw + ". Levantamento não validado; metas e declarações qualitativas permanecem sem valor numérico.");
        indicadores.save(metric);
        return 1;
    }
}
