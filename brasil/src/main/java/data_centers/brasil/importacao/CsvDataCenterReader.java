package data_centers.brasil.importacao;

import data_centers.brasil.exception.CsvImportException;
import data_centers.brasil.model.DataCenterImportacao;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class CsvDataCenterReader {
    public static final int MAX_ROWS = 50_000;
    public static final List<String> HEADERS = List.of(
        "id", "operador", "unidade", "municipio", "uf", "codigo_ibge", "endereco", "latitude", "longitude", "precisao_geo", "tipo_instalacao", "status_operacional", "inicio_operacao", "potencia_ti_mw_unidade", "potencia_campus_mw", "area_m2_unidade", "redes_conectadas", "ix_conectados", "carriers", "voltada_ia", "resfriamento", "fonte_energia", "pue_declarado", "wue_declarado", "certificacoes", "robustez_evidencia", "fonte_url", "data_levantamento"
    );

    /** Fecha o stream recebido; interpreta BOM, aspas e delimitador do levantamento. */
    public List<DataCenterImportacao> read(InputStream input) {
        try (var reader = new PushbackReader(new InputStreamReader(input, StandardCharsets.UTF_8), 1)) {
            int first = reader.read();
            if (first != -1 && first != 0xFEFF) reader.unread(first);
            var format = CSVFormat.DEFAULT.builder()
                .setDelimiter(';').setHeader().setSkipHeaderRecord(true).get();
            try (var parser = format.parse(reader)) {
                if (!parser.getHeaderNames().equals(HEADERS)) {
                    throw new CsvImportException("Cabeçalho inválido. Use as 28 colunas do levantamento original, na mesma ordem.");
                }
                var rows = new ArrayList<DataCenterImportacao>();
                for (CSVRecord row : parser) {
                    if (!row.isConsistent()) {
                        throw new CsvImportException("Registro " + row.getRecordNumber() + ": quantidade de colunas inválida.");
                    }
                    if (rows.size() >= MAX_ROWS) throw new CsvImportException("Máximo de " + MAX_ROWS + " registros.");
                    rows.add(new DataCenterImportacao(
                        row.get("id"),
                        row.get("operador"),
                        row.get("unidade"),
                        row.get("municipio"),
                        row.get("uf"),
                        row.get("codigo_ibge"),
                        row.get("endereco"),
                        row.get("latitude"),
                        row.get("longitude"),
                        row.get("precisao_geo"),
                        row.get("tipo_instalacao"),
                        row.get("status_operacional"),
                        row.get("inicio_operacao"),
                        row.get("potencia_ti_mw_unidade"),
                        row.get("potencia_campus_mw"),
                        row.get("area_m2_unidade"),
                        row.get("redes_conectadas"),
                        row.get("ix_conectados"),
                        row.get("carriers"),
                        row.get("voltada_ia"),
                        row.get("resfriamento"),
                        row.get("fonte_energia"),
                        row.get("pue_declarado"),
                        row.get("wue_declarado"),
                        row.get("certificacoes"),
                        row.get("robustez_evidencia"),
                        row.get("fonte_url"),
                        row.get("data_levantamento")
                    ));
                }
                if (rows.isEmpty()) throw new CsvImportException("O CSV não possui registros.");
                return List.copyOf(rows);
            }
        } catch (IOException | UncheckedIOException ex) {
            throw new CsvImportException("Não foi possível ler o CSV: " + ex.getMessage(), ex);
        }
    }
}
