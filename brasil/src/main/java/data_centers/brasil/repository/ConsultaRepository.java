package data_centers.brasil.repository;
import data_centers.brasil.filter.*;
import java.util.ArrayList;
import data_centers.brasil.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository @RequiredArgsConstructor
public class ConsultaRepository {
    private final JdbcTemplate jdbc;
    public List<DashboardResponse> dashboard(List<FilterCondition> conditions) {
        var where = SqlFilters.clause(conditions, false);
        var having = SqlFilters.clause(conditions, true);
        var arguments = new ArrayList<>(where.arguments());
        arguments.addAll(having.arguments());
        String sql = "SELECT cidade, COUNT(*) AS quantidade, COALESCE(SUM(potencia_mw), 0) AS potencia FROM data_center"
            + where.sql() + " GROUP BY cidade" + having.sql() + " ORDER BY quantidade DESC, cidade";
        return jdbc.query(sql,
            (rs, row) -> new DashboardResponse(rs.getString("cidade"), rs.getLong("quantidade"), rs.getBigDecimal("potencia")), arguments.toArray());
    }
}
