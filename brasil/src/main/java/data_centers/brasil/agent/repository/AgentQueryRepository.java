package data_centers.brasil.agent.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;
import data_centers.brasil.filter.*;
import org.springframework.util.MultiValueMap;

/** Fixed read-only queries for aggregates and news not provided by existing services. */
@Repository @RequiredArgsConstructor
public class AgentQueryRepository {
    private final JdbcTemplate jdbc;
    private final QueryFilterParser filterParser;
    public List<Map<String, Object>> estados(int size, long offset) {
        return jdbc.queryForList("SELECT UPPER(TRIM(estado)) AS estado, COUNT(*) AS quantidade FROM data_center GROUP BY UPPER(TRIM(estado)) ORDER BY quantidade DESC, estado LIMIT ? OFFSET ?", size, offset);
    }
    public long municipios() {
        String city=TextNormalization.sql("cidade");
        return jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT "+city+", UPPER(TRIM(estado)) FROM data_center GROUP BY "+city+", UPPER(TRIM(estado))) municipios",Long.class);
    }
    public Map<String,Object> municipios(int size, long offset, MultiValueMap<String,String> filters, boolean ranking) {
        var clause=SqlFilters.clause(filterParser.parse(filters, FilterResource.DATA_CENTER),false);
        String city=TextNormalization.sql("cidade"), state="UPPER(TRIM(estado))";
        String grouped=" FROM data_center WHERE cidade IS NOT NULL AND "+city+" <> ''"
            +(clause.sql().isEmpty()?"":" AND "+clause.sql().substring(7))
            +" GROUP BY "+state+", "+city;
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT 1"+grouped+") m",Long.class,clause.arguments().toArray());
        var args=new ArrayList<Object>(clause.arguments()); args.add(size); args.add(offset);
        var rows=jdbc.query("SELECT MIN(TRIM(cidade)) AS municipio, "+state+" AS estado, COUNT(*) AS quantidade"+grouped
            +" ORDER BY "+(ranking?"quantidade DESC, ":"")+state+", "+city+" LIMIT ? OFFSET ?",
            (rs,n)->new data_centers.brasil.agent.dto.MunicipioCount(rs.getString("municipio"),rs.getString("estado"),rs.getLong("quantidade")),args.toArray());
        return Map.of("registros",rows,"totalMunicipios",total);
    }
    public List<Map<String, Object>> operadores(int size, long offset, MultiValueMap<String,String> filters) {
        var clause = SqlFilters.clause(filterParser.parse(filters, FilterResource.DATA_CENTER),false);
        var args = new ArrayList<Object>(clause.arguments()); args.add(size); args.add(offset);
        return jdbc.queryForList("SELECT empresa, COUNT(*) AS quantidade FROM data_center WHERE empresa IS NOT NULL AND TRIM(empresa) <> '' " + (clause.sql().isEmpty() ? "" : " AND " + clause.sql().substring(7)) + " GROUP BY empresa ORDER BY quantidade DESC, empresa LIMIT ? OFFSET ?", args.toArray());
    }
    public List<Map<String, Object>> noticias(String term, int size, long offset) {
        return jdbc.queryForList("SELECT id, titulo, LEFT(descricao, 2000) AS descricao, url, fonte, categoria, data_publicacao FROM noticia WHERE POSITION(? IN LOWER(CONCAT(titulo, ' ', descricao, ' ', categoria))) > 0 ORDER BY data_publicacao DESC NULLS LAST, id DESC LIMIT ? OFFSET ?", term == null ? "" : term.toLowerCase(Locale.ROOT), size, offset);
    }
}
