package data_centers.brasil.agent.sql;

import java.util.*;

/** Raw SQL belongs to backend evidence, not to the model's tool-response context. */
public final class AgentSqlEvidence {
    private AgentSqlEvidence() {}

    public static Map<String,Object> attach(Map<String,Object> result,List<String> queries) {
        var copy=new LinkedHashMap<>(result);
        if(!queries.isEmpty()) copy.put("_sql",List.copyOf(queries));
        return copy;
    }

    public static List<String> queries(Map<String,Object> result) { return strings(result.get("_sql")); }
    public static List<String> strings(Object value) {
        if(!(value instanceof List<?> values)) return List.of();
        return values.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }

    public static Map<String,Object> historical(Map<String,Object> result) {
        var copy=new LinkedHashMap<>(result);copy.put("_historical",true);return copy;
    }

    public static Map<String,Object> publicResult(Map<String,Object> result) {
        var copy=new LinkedHashMap<>(result);copy.keySet().removeIf(key->key.startsWith("_"));return copy;
    }
}
