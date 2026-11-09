package data_centers.brasil.agent.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import java.util.*;

/** Retrieval pagination is independent of visual pagination and never implies top ten. */
@Service
public class AgentQueryPaginationService {
    private final AgentDataBaseService database;
    @Value("${agent.query.max-records:2000}") private int maxRecords=2000;
    @Value("${agent.query.max-pages:100}") private int maxPages=100;
    public AgentQueryPaginationService(AgentDataBaseService database) { this.database=database; }
    public Map<String,Object> execute(Map<?,?> arguments,boolean all) {
        var args=new LinkedHashMap<String,Object>();arguments.forEach((k,v)->args.put((String)k,v));
        if(all && "MUNICIPIOS".equals(args.get("operacao"))) args.put("todos",true);
        var first=new LinkedHashMap<>(database.executar(args));
        first.put("_datasetId",UUID.randomUUID().toString());
        first.put("_request",Map.copyOf(args));
        if(!all || "MUNICIPIOS".equals(args.get("operacao")) || "CONTAR".equals(args.get("operacao"))) return first;
        // A complete request always begins at page zero; preserve exact filters/order.
        int page=((Number)args.getOrDefault("pagina",0)).intValue();
        if(page!=0) { args.put("pagina",0);first=new LinkedHashMap<>(database.executar(args));first.put("_request",Map.copyOf(args));first.put("_datasetId",UUID.randomUUID().toString()); }
        int size=((Number)args.getOrDefault("limite",20)).intValue();
        var records=new ArrayList<Object>();
        var result=first;long total=-1;boolean next=true;int pages=0;
        while(true) {
            Object raw=result.get("resultado");
            Map<?,?> meta=raw instanceof Map<?,?> m?m:Map.of();
            List<?> rows=raw instanceof List<?> list?list:meta.get("registros") instanceof List<?> list?list:List.of();
            if(rows.isEmpty() && pages==0 && !(raw instanceof List<?>) && !meta.containsKey("registros")) return first;
            if(meta.get("total") instanceof Number n) total=n.longValue();
            int take=Math.min(rows.size(),Math.max(0,maxRecords-records.size()));records.addAll(rows.subList(0,take));pages++;
            next=meta.get("temProximaPagina") instanceof Boolean b?b:rows.size()==size;
            if(take<rows.size()) next=true;
            if(!next || records.size()>=maxRecords || pages>=maxPages) break;
            args.put("pagina",++page);result=new LinkedHashMap<>(database.executar(args));
        }
        var meta=new LinkedHashMap<String,Object>();meta.put("registros",records);meta.put("totalElements",total>=0?total:next?records.size()+1L:(long)records.size());
        meta.put("totalKnown",total>=0 || !next);meta.put("completo",!next);meta.put("hasNext",next);meta.put("page",0);meta.put("size",size);meta.put("paginasConsultadas",pages);
        meta.put("nextPage",records.size()/size);
        first.put("resultado",meta);first.put("pagina",0);first.put("limite",size);
        var request=new LinkedHashMap<String,Object>(args);request.put("pagina",0);first.put("_request",request);first.put("_all",true);
        return first;
    }
}
