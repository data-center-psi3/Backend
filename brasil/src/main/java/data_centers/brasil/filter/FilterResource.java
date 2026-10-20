package data_centers.brasil.filter;

import java.util.*;
import static data_centers.brasil.filter.FilterField.Type.*;

public enum FilterResource {
    DATA_CENTER(List.of(
        FilterField.of("id", "id", "id", INTEGER),
        FilterField.of("nome", "nome", "nome", TEXT),
        FilterField.of("empresa", "empresa", "empresa", TEXT),
        FilterField.of("cidade", "cidade", "cidade", TEXT),
        FilterField.of("estado", "estado", "estado", TEXT),
        FilterField.of("pais", "pais", "pais", TEXT),
        FilterField.of("latitude", "latitude", "latitude", DECIMAL),
        FilterField.of("longitude", "longitude", "longitude", DECIMAL),
        FilterField.of("descricao", "descricao", "descricao", TEXT),
        FilterField.of("potenciaMw", "potenciaMw", "potencia_mw", DECIMAL),
        FilterField.of("areaM2", "areaM2", "area_m2", DECIMAL),
        FilterField.of("tipoInstalacao", "tipoInstalacao", "tipo_instalacao", TEXT),
        FilterField.of("statusValidacao", "statusValidacao", "status_validacao", TEXT),
        FilterField.of("dataValidacao", "dataValidacao", "data_validacao", DATE)
    )),
    FONTE(List.of(
        FilterField.of("id", "id", "id", INTEGER),
        FilterField.of("instituicao", "instituicao", "instituicao", TEXT),
        FilterField.of("titulo", "titulo", "titulo", TEXT),
        FilterField.of("url", "url", "url", TEXT),
        FilterField.of("dataPublicacao", "dataPublicacao", "data_publicacao", DATE),
        FilterField.of("dataConsulta", "dataConsulta", "data_consulta", DATE)
    )),
    INDICADOR_AMBIENTAL(List.of(
        FilterField.of("id", "id", "id", INTEGER),
        FilterField.of("dataCenterId", "dataCenter.id", "data_center_id", INTEGER),
        FilterField.of("tipo", "tipo", "tipo", TEXT),
        FilterField.of("valor", "valor", "valor", DECIMAL),
        FilterField.of("unidade", "unidade", "unidade", TEXT),
        FilterField.of("periodo", "periodo", "periodo", TEXT),
        FilterField.of("fonteId", "fonte.id", "fonte_id", INTEGER),
        FilterField.of("observacoes", "observacoes", "observacoes", TEXT)
    )),
    BENEFICIO(List.of(
        FilterField.of("id", "id", "id", INTEGER),
        FilterField.of("dataCenterId", "dataCenter.id", "data_center_id", INTEGER),
        FilterField.of("tipo", "tipo", "tipo", TEXT),
        FilterField.of("descricao", "descricao", "descricao", TEXT),
        FilterField.of("fonteId", "fonte.id", "fonte_id", INTEGER)
    )),
    CONTEUDO_PESQUISA(List.of(
        FilterField.of("id", "id", "id", INTEGER),
        FilterField.of("dataCenterId", "dataCenter.id", "data_center_id", INTEGER),
        FilterField.of("titulo", "titulo", "titulo", TEXT),
        FilterField.of("conteudo", "conteudo", "conteudo", TEXT),
        FilterField.of("categoria", "categoria", "categoria", TEXT),
        FilterField.of("fonteId", "fonte.id", "fonte_id", INTEGER),
        FilterField.of("dataPublicacao", "dataPublicacao", "data_publicacao", DATE)
    )),
    IMPORTACAO(List.of(
        FilterField.of("idOriginal", "idOriginal", "id_original", TEXT),
        FilterField.of("operador", "operador", "operador", TEXT),
        FilterField.of("unidade", "unidade", "unidade", TEXT),
        FilterField.of("municipio", "municipio", "municipio", TEXT),
        FilterField.of("uf", "uf", "uf", TEXT),
        FilterField.of("codigoIbge", "codigoIbge", "codigo_ibge", TEXT),
        FilterField.of("endereco", "endereco", "endereco", TEXT),
        FilterField.of("latitude", "latitude", "latitude", TEXT),
        FilterField.of("longitude", "longitude", "longitude", TEXT),
        FilterField.of("precisaoGeo", "precisaoGeo", "precisao_geo", TEXT),
        FilterField.of("tipoInstalacao", "tipoInstalacao", "tipo_instalacao", TEXT),
        FilterField.of("statusOperacional", "statusOperacional", "status_operacional", TEXT),
        FilterField.of("inicioOperacao", "inicioOperacao", "inicio_operacao", TEXT),
        FilterField.of("potenciaTiMwUnidade", "potenciaTiMwUnidade", "potencia_ti_mw_unidade", TEXT),
        FilterField.of("potenciaCampusMw", "potenciaCampusMw", "potencia_campus_mw", TEXT),
        FilterField.of("areaM2Unidade", "areaM2Unidade", "area_m2_unidade", TEXT),
        FilterField.of("redesConectadas", "redesConectadas", "redes_conectadas", TEXT),
        FilterField.of("ixConectados", "ixConectados", "ix_conectados", TEXT),
        FilterField.of("carriers", "carriers", "carriers", TEXT),
        FilterField.of("voltadaIa", "voltadaIa", "voltada_ia", TEXT),
        FilterField.of("resfriamento", "resfriamento", "resfriamento", TEXT),
        FilterField.of("fonteEnergia", "fonteEnergia", "fonte_energia", TEXT),
        FilterField.of("pueDeclarado", "pueDeclarado", "pue_declarado", TEXT),
        FilterField.of("wueDeclarado", "wueDeclarado", "wue_declarado", TEXT),
        FilterField.of("certificacoes", "certificacoes", "certificacoes", TEXT),
        FilterField.of("robustezEvidencia", "robustezEvidencia", "robustez_evidencia", TEXT),
        FilterField.of("fonteUrl", "fonteUrl", "fonte_url", TEXT),
        FilterField.of("dataLevantamento", "dataLevantamento", "data_levantamento", TEXT)
    )),
    DASHBOARD(dashboardFields());

    private final Map<String, FilterField> fields;
    FilterResource(List<FilterField> fields) {
        var map = new LinkedHashMap<String, FilterField>();
        fields.forEach(field -> map.put(field.name(), field));
        this.fields = Collections.unmodifiableMap(map);
    }
    public Map<String, FilterField> fields() { return fields; }
    public boolean regional() { return this == DATA_CENTER || this == DASHBOARD; }

    private static List<FilterField> dashboardFields() {
        var result = new ArrayList<>(DATA_CENTER.fields.values());
        result.add(new FilterField("quantidadeDataCenters", "", "COUNT(*)", LONG, true));
        result.add(new FilterField("potenciaTotalMw", "", "COALESCE(SUM(potencia_mw), 0)", DECIMAL, true));
        return result;
    }
}
