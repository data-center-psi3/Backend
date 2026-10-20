package data_centers.brasil.repository;
import data_centers.brasil.filter.*;
import java.util.ArrayList;
import data_centers.brasil.model.DataCenterImportacao;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository @RequiredArgsConstructor
public class DataCenterImportacaoRepository {
    private final JdbcTemplate jdbc;
    public void insert(DataCenterImportacao row) {
        jdbc.update("INSERT INTO data_center_importacao (id_original, operador, unidade, municipio, uf, codigo_ibge, endereco, latitude, longitude, precisao_geo, tipo_instalacao, status_operacional, inicio_operacao, potencia_ti_mw_unidade, potencia_campus_mw, area_m2_unidade, redes_conectadas, ix_conectados, carriers, voltada_ia, resfriamento, fonte_energia, pue_declarado, wue_declarado, certificacoes, robustez_evidencia, fonte_url, data_levantamento) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            row.idOriginal(), row.operador(), row.unidade(), row.municipio(), row.uf(), row.codigoIbge(), row.endereco(), row.latitude(), row.longitude(), row.precisaoGeo(), row.tipoInstalacao(), row.statusOperacional(), row.inicioOperacao(), row.potenciaTiMwUnidade(), row.potenciaCampusMw(), row.areaM2Unidade(), row.redesConectadas(), row.ixConectados(), row.carriers(), row.voltadaIa(), row.resfriamento(), row.fonteEnergia(), row.pueDeclarado(), row.wueDeclarado(), row.certificacoes(), row.robustezEvidencia(), row.fonteUrl(), row.dataLevantamento());
    }
    public List<DataCenterImportacao> list(int limit, long offset, List<FilterCondition> conditions) {
        var where = SqlFilters.clause(conditions, false);
        var arguments = new ArrayList<>(where.arguments());
        arguments.add(limit);
        arguments.add(offset);
        return jdbc.query("SELECT * FROM data_center_importacao" + where.sql() + " ORDER BY id_original NULLS LAST, operador NULLS LAST, unidade NULLS LAST LIMIT ? OFFSET ?",
            (rs, index) -> new DataCenterImportacao(
                rs.getString("id_original"),
                rs.getString("operador"),
                rs.getString("unidade"),
                rs.getString("municipio"),
                rs.getString("uf"),
                rs.getString("codigo_ibge"),
                rs.getString("endereco"),
                rs.getString("latitude"),
                rs.getString("longitude"),
                rs.getString("precisao_geo"),
                rs.getString("tipo_instalacao"),
                rs.getString("status_operacional"),
                rs.getString("inicio_operacao"),
                rs.getString("potencia_ti_mw_unidade"),
                rs.getString("potencia_campus_mw"),
                rs.getString("area_m2_unidade"),
                rs.getString("redes_conectadas"),
                rs.getString("ix_conectados"),
                rs.getString("carriers"),
                rs.getString("voltada_ia"),
                rs.getString("resfriamento"),
                rs.getString("fonte_energia"),
                rs.getString("pue_declarado"),
                rs.getString("wue_declarado"),
                rs.getString("certificacoes"),
                rs.getString("robustez_evidencia"),
                rs.getString("fonte_url"),
                rs.getString("data_levantamento")
            ), arguments.toArray());
    }
    public long count() { return jdbc.queryForObject("SELECT COUNT(*) FROM data_center_importacao", Long.class); }
    public long count(List<FilterCondition> conditions) {
        var where = SqlFilters.clause(conditions, false);
        return jdbc.queryForObject("SELECT COUNT(*) FROM data_center_importacao" + where.sql(), Long.class, where.arguments().toArray());
    }
    public boolean existsByIdOriginal(String id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM data_center_importacao WHERE id_original = ?", Long.class, id) > 0;
    }
}
