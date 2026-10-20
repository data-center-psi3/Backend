package data_centers.brasil.importacao;

import data_centers.brasil.dto.*;
import data_centers.brasil.exception.CsvImportException;
import data_centers.brasil.model.DataCenterImportacao;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

@Component
@RequiredArgsConstructor
public class DataCenterCsvNormalizer {
    private final Validator validator;

    public RegistroNormalizado normalize(DataCenterImportacao row) {
        var avisos = new ArrayList<CargaAviso>();
        String id = text(row.idOriginal());
        if (id == null || id.length() > 150) throw new CsvImportException("Identificador original ausente ou maior que 150 caracteres.");
        var request = new DataCenterRequest(
            text(row.unidade()), text(row.operador()), text(row.municipio()),
            text(row.uf()) == null ? null : row.uf().strip().toUpperCase(Locale.ROOT),
            "Brasil",
            number(id, "latitude", row.latitude(), 7, avisos),
            number(id, "longitude", row.longitude(), 7, avisos),
            research(row),
            number(id, "potencia_ti_mw_unidade", row.potenciaTiMwUnidade(), 2, avisos),
            number(id, "area_m2_unidade", row.areaM2Unidade(), 2, avisos),
            text(row.tipoInstalacao()), "PENDENTE", null
        );
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            var messages = violations.stream().map(v -> v.getPropertyPath() + ": " + v.getMessage()).sorted().toList();
            throw new CsvImportException("Registro " + id + " inválido: " + String.join("; ", messages));
        }
        LocalDate levantamento = null;
        if (text(row.dataLevantamento()) != null) {
            try { levantamento = LocalDate.parse(row.dataLevantamento().strip()); }
            catch (DateTimeParseException ex) { throw new CsvImportException("Registro " + id + ": data_levantamento deve usar YYYY-MM-DD."); }
        }
        // Valida também os indicadores antes de qualquer gravação.
        for (var value : List.of(
            new String[]{"pue_declarado", Objects.toString(row.pueDeclarado(), "")},
            new String[]{"wue_declarado", Objects.toString(row.wueDeclarado(), "")}
        )) {
            var parsed = number(id, value[0], value[1], 4, avisos);
            if (parsed != null && (parsed.signum() < 0 || parsed.precision() - parsed.scale() > 11)) {
                throw new CsvImportException("Registro " + id + ": " + value[0] + " fora do limite numérico.");
            }
        }
        return new RegistroNormalizado(row, request, levantamento, request.descricao(), List.copyOf(avisos));
    }

    /** Somente números exatos: não transforma metas, intervalos ou declarações em medidas. */
    public BigDecimal number(String id, String field, String raw, int scale, List<CargaAviso> avisos) {
        String value = text(raw);
        if (value == null) return null;
        if (!value.matches("[+-]?\\d+(?:[.,]\\d+)?")) {
            avisos.add(new CargaAviso(id, field, raw, "Texto preservado; não convertido para número."));
            return null;
        }
        var number = new BigDecimal(value.replace(',', '.'));
        if (number.scale() > scale) {
            avisos.add(new CargaAviso(id, field, raw, "Arredondado para " + scale + " casas; original preservado."));
            number = number.setScale(scale, RoundingMode.HALF_UP);
        }
        return number;
    }

    public String text(String raw) {
        if (raw == null) return null;
        String value = raw.strip();
        if (value.isEmpty() || Set.of("—", "–", "-", "n/a", "na", "null").contains(value.toLowerCase(Locale.ROOT))) return null;
        return value;
    }

    private String research(DataCenterImportacao row) {
        return "Levantamento CSV — dados declarados, sujeitos a validação.\n" +
            "id_original: " + Objects.toString(row.idOriginal(), "") + "\n" +
            "operador: " + Objects.toString(row.operador(), "") + "\n" +
            "unidade: " + Objects.toString(row.unidade(), "") + "\n" +
            "municipio: " + Objects.toString(row.municipio(), "") + "\n" +
            "uf: " + Objects.toString(row.uf(), "") + "\n" +
            "codigo_ibge: " + Objects.toString(row.codigoIbge(), "") + "\n" +
            "endereco: " + Objects.toString(row.endereco(), "") + "\n" +
            "latitude: " + Objects.toString(row.latitude(), "") + "\n" +
            "longitude: " + Objects.toString(row.longitude(), "") + "\n" +
            "precisao_geo: " + Objects.toString(row.precisaoGeo(), "") + "\n" +
            "tipo_instalacao: " + Objects.toString(row.tipoInstalacao(), "") + "\n" +
            "status_operacional: " + Objects.toString(row.statusOperacional(), "") + "\n" +
            "inicio_operacao: " + Objects.toString(row.inicioOperacao(), "") + "\n" +
            "potencia_ti_mw_unidade: " + Objects.toString(row.potenciaTiMwUnidade(), "") + "\n" +
            "potencia_campus_mw: " + Objects.toString(row.potenciaCampusMw(), "") + "\n" +
            "area_m2_unidade: " + Objects.toString(row.areaM2Unidade(), "") + "\n" +
            "redes_conectadas: " + Objects.toString(row.redesConectadas(), "") + "\n" +
            "ix_conectados: " + Objects.toString(row.ixConectados(), "") + "\n" +
            "carriers: " + Objects.toString(row.carriers(), "") + "\n" +
            "voltada_ia: " + Objects.toString(row.voltadaIa(), "") + "\n" +
            "resfriamento: " + Objects.toString(row.resfriamento(), "") + "\n" +
            "fonte_energia: " + Objects.toString(row.fonteEnergia(), "") + "\n" +
            "pue_declarado: " + Objects.toString(row.pueDeclarado(), "") + "\n" +
            "wue_declarado: " + Objects.toString(row.wueDeclarado(), "") + "\n" +
            "certificacoes: " + Objects.toString(row.certificacoes(), "") + "\n" +
            "robustez_evidencia: " + Objects.toString(row.robustezEvidencia(), "") + "\n" +
            "fonte_url: " + Objects.toString(row.fonteUrl(), "") + "\n" +
            "data_levantamento: " + Objects.toString(row.dataLevantamento(), "") + "\n";
    }
}
