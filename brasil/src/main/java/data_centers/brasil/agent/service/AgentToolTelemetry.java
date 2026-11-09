package data_centers.brasil.agent.service;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;

@Component
public class AgentToolTelemetry {
    private static final Logger LOG=LoggerFactory.getLogger(AgentToolTelemetry.class);
    private final JsonMapper json=JsonMapper.builder().build();
    public void record(UUID conversation,String tool,Map<?,?> args,Map<String,Object> result,long nanos,boolean cached,RuntimeException error) {
        var filters=new TreeMap<String,Object>();
        for(String key:List.of("estado","cidade","dataCenterId","fonteId","estadoA","estadoB","percentualSobre"))
            if(args.containsKey(key)) {
                Object value=args.get(key);
                if(value instanceof String s) {
                    try { value=key.equals("cidade") ? data_centers.brasil.filter.TextNormalization.normalize(s)
                        : data_centers.brasil.filter.TextNormalization.state(s); }
                    catch(IllegalArgumentException ignored) { value="invalid"; }
                }
                filters.put(key,value instanceof Number ? value : value instanceof String s ? s.replaceAll("[\r\n]"," ").substring(0,Math.min(s.length(),100)) : "invalid");
            }
        for(String key:List.of("nome","empresa","termo")) if(args.containsKey(key)) filters.put(key,"[redacted]");
        Object count=null;
        if(result!=null) {
            Object rows=result.get("resultado");
            if(rows instanceof List<?> list) count=list.size();
            else if(rows instanceof Map<?,?> m) count=m.get("registros") instanceof List<?> records ? records.size()
                : m.containsKey("total")?m.get("total"):m.containsKey("totalMunicipios")?m.get("totalMunicipios"):m.containsKey("estadoA")?1:0;
        }
        var event=new LinkedHashMap<String,Object>();
        event.put("event","agent_tool"); event.put("conversationId",conversation.toString());
        event.put("tool",Set.of("consultar_dataterra","resposta_conceitual","comparar_contagens","conversation_memory","planejar_visualizacao").contains(tool)?tool:"unknown");
        Object op=args.get("operacao");
        event.put("operation",op instanceof String s && s.matches("[A-Z_]{1,30}")?s:tool.equals("comparar_contagens")?"COMPARAR":"none");
        event.put("filters",filters); event.put("resultCount",count); event.put("durationMs",nanos/1_000_000.0);
        event.put("cached",cached); event.put("status",error==null?"success":"error");
        if(error!=null) event.put("errorType",error.getClass().getSimpleName());
        LOG.info("{}",json.writeValueAsString(event));
    }
}
