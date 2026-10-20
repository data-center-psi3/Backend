package data_centers.brasil.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository @RequiredArgsConstructor
public class RadarRepository {
    private final JdbcTemplate jdbc;

    /** Projeção restrita: não carrega entidades, fontes, pesquisas ou campos sensíveis. */
    public List<Row> rows() {
        return jdbc.query("""
            SELECT id_original, operador, unidade, municipio, uf, codigo_ibge, latitude, longitude,
                   precisao_geo, tipo_instalacao, redes_conectadas, ix_conectados, carriers
            FROM data_center_importacao
            """, (rs, n) -> new Row(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9),
                rs.getString(10), rs.getString(11), rs.getString(12), rs.getString(13)));
    }
    public record Row(String id, String operator, String unit, String city, String uf, String ibge,
        String latitude, String longitude, String precision, String type, String networks, String ix,
        String carriers) {}
}
