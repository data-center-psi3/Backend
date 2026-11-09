package data_centers.brasil.agent.visualization;

import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** Adapts authorized tool envelopes, never performs joins between unrelated queries. */
@Service
public class AgentDatasetService {
    private final JsonMapper json=JsonMapper.builder().build();
    public record Dataset(String id,List<Map<String,Object>> data,Map<String,String> fields,long total,
                          boolean complete,List<Map<String,Object>> queries) {}

    @SuppressWarnings("unchecked")
    public List<Dataset> datasets(List<Map<String,Object>> evidence) {
        var groups=new LinkedHashMap<String,List<Map<String,Object>>>();
        for(var e:evidence) {
            if(!e.containsKey("operacao") || "VISUALIZATION".equals(e.get("operacao"))) continue;
            String id=id(e);
            groups.computeIfAbsent(id,k->new ArrayList<>()).add(e);
        }
        var output=new ArrayList<Dataset>();
        for(var group:groups.entrySet()) {
            var uniquePages=new LinkedHashMap<String,Map<String,Object>>();
            for(var e:group.getValue()) {
                Object raw=e.get("resultado");
                int count=raw instanceof List<?> l?l.size():raw instanceof Map<?,?> m && m.get("registros") instanceof List<?> l?l.size():0;
                uniquePages.put(e.getOrDefault("pagina",0)+":"+e.getOrDefault("limite",20)+":"+count,e);
            }
            var queries=List.copyOf(uniquePages.values());
            var rows=new TreeMap<Long,Map<String,Object>>();
            var intervals=new TreeMap<Long,Long>();
            long total=-1, end=-1; boolean incomplete=false;
            for(var envelope:queries) {
                Object result=envelope.get("resultado");
                Map<?,?> meta=result instanceof Map<?,?> m?m:Map.of();
                List<?> items=result instanceof List<?> list?list:meta.get("registros") instanceof List<?> list?list:List.of();
                if(result instanceof Map<?,?> scalar && !scalar.containsKey("registros") && !scalar.isEmpty()) {
                    var row=new LinkedHashMap<String,Object>();
                    scalar.forEach((k,v)->{ if(k instanceof String s && (v==null || v instanceof String || v instanceof Number || v instanceof Boolean)) row.put(s,v); });
                    if(envelope.get("consulta") instanceof Map<?,?> filters) filters.forEach((k,v)->{ if(k instanceof String s && v instanceof String) row.putIfAbsent(s,v); });
                    if(row.values().stream().anyMatch(Number.class::isInstance)) {
                        items=List.of(row);meta=Map.of("totalElements",1,"completo",true,"hasNext",false);
                    }
                }
                long page=number(meta.get("page"),number(envelope.get("pagina"),0));
                long size=number(meta.get("size"),number(envelope.get("limite"),20));
                long offset=page*size;
                if(Boolean.TRUE.equals(meta.get("completo"))) offset=0;
                intervals.merge(offset,offset+items.size(),Math::max);
                Object count=meta.containsKey("totalElements")?meta.get("totalElements"):meta.containsKey("total")?meta.get("total"):meta.get("totalMunicipios");
                if(count instanceof Number n && !Boolean.FALSE.equals(meta.get("totalKnown"))) total=Math.max(total,n.longValue());
                if(Boolean.FALSE.equals(meta.get("hasNext")) || Boolean.FALSE.equals(meta.get("temProximaPagina")) || items.size()<size) end=offset+items.size();
                incomplete|=Boolean.TRUE.equals(envelope.get("_requiresRefresh"));
                for(Object item:items) {
                    if(item==null) continue;
                    Map<String,Object> row=json.convertValue(item,Map.class);
                    if(row.containsKey("limitacao")) { incomplete=true; continue; }
                    // Page offsets preserve duplicate observations and SQL ordering.
                    rows.put(offset++,Collections.unmodifiableMap(new LinkedHashMap<>(row)));
                }
            }
            long covered=0;
            for(var interval:intervals.entrySet()) { if(interval.getKey()>covered) break; covered=Math.max(covered,interval.getValue()); }
            if(total<0) total=end>=0?end:-1;
            var data=List.copyOf(rows.values());
            boolean complete=!incomplete && covered>=total && (end>=0 || queries.stream().anyMatch(e->e.get("resultado") instanceof Map<?,?> m && Boolean.TRUE.equals(m.get("completo"))));
            output.add(new Dataset(group.getKey(),data,infer(data),total,complete,queries));
        }
        return output;
    }
    public String id(Map<String,Object> e) {
        if(e.get("_datasetId") instanceof String id) return id;
        var identity=new TreeMap<String,Object>();
        identity.put("operacao",e.get("operacao")); identity.put("consulta",e.getOrDefault("consulta",Map.of()));
        return UUID.nameUUIDFromBytes(json.writeValueAsString(identity).getBytes(StandardCharsets.UTF_8)).toString();
    }
    private Map<String,String> infer(List<Map<String,Object>> rows) {
        var fields=new LinkedHashMap<String,String>();
        for(var row:rows) for(var entry:row.entrySet()) {
            Object value=entry.getValue(); if(value==null) { fields.putIfAbsent(entry.getKey(),"unknown"); continue; }
            String type=value instanceof Number?"quantitative":value instanceof java.time.temporal.TemporalAccessor || temporal(value)?"temporal":value instanceof String || value instanceof Boolean?"nominal":"unsupported";
            fields.merge(entry.getKey(),type,(a,b)->a.equals("unknown")?b:a.equals(b)?a:"nominal");
        }
        return Collections.unmodifiableMap(fields);
    }
    private boolean temporal(Object v) {
        if(!(v instanceof String s)) return false;
        try { java.time.LocalDate.parse(s); return true; } catch(java.time.DateTimeException ignored) {}
        try { java.time.Instant.parse(s); return true; } catch(java.time.DateTimeException ignored) {}
        return s.matches("(?:19|20)\\d{2}(?:-(?:0[1-9]|1[0-2]))?");
    }
    public Map<String,Object> describe(Dataset d) {
        return Map.of("datasetId",d.id(),"fields",d.fields(),"returnedRecords",d.data().size(),"totalRecords",d.total(),"complete",d.complete(),"queries",d.queries().stream().map(e->Map.of("operation",e.get("operacao"),"filters",e.getOrDefault("consulta",Map.of()))).toList());
    }
    public Object modelResult(Map<String,Object> e) {
        if(e.get("resultado") instanceof Map<?,?> m && !m.containsKey("registros")) return data_centers.brasil.agent.sql.AgentSqlEvidence.publicResult(e);
        var sets=datasets(List.of(e));
        if(sets.isEmpty() || sets.getFirst().data().isEmpty()) return data_centers.brasil.agent.sql.AgentSqlEvidence.publicResult(e);
        var description=new LinkedHashMap<String,Object>(describe(sets.getFirst()));
        if(e.get("_visualization") instanceof Map<?,?> previous) description.put("lastVisualization",previous);
        return description;
    }
    private static long number(Object value,long fallback) { return value instanceof Number n?n.longValue():fallback; }
}
