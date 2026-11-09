package data_centers.brasil.agent.service;

import org.springframework.stereotype.Service;
import data_centers.brasil.filter.TextNormalization;
import java.math.*;
import java.util.*;
import data_centers.brasil.agent.sql.AgentSqlCapture;
import data_centers.brasil.agent.sql.AgentSqlEvidence;

/** Arithmetic uses only successful database counts, never model-provided numbers. */
@Service
public class AgentComparisonService {
    private final AgentDataBaseService database;
    public AgentComparisonService(AgentDataBaseService database) { this.database=database; }
    public Map<String,Object> executar(Map<?,?> args,List<Map<String,Object>> evidence) {
        if(!Set.of("estadoA","estadoB","percentualSobre","atualizar").containsAll(args.keySet())) throw new IllegalArgumentException("Argumento de comparação desconhecido.");
        String a=state(args,"estadoA"), b=state(args,"estadoB");
        if(a.equals(b)) throw new IllegalArgumentException("Informe dois estados diferentes.");
        String denominator=args.containsKey("percentualSobre")?state(args,"percentualSobre"):null;
        if(denominator!=null && !Set.of(a,b).contains(denominator)) throw new IllegalArgumentException("O denominador deve ser um dos estados comparados.");
        if(args.containsKey("atualizar") && !(args.get("atualizar") instanceof Boolean)) throw new IllegalArgumentException("atualizar deve ser booleano.");
        boolean refresh=Boolean.TRUE.equals(args.get("atualizar"));
        var ca=count(a,evidence,refresh); var cb=count(b,evidence,refresh);
        long va=total(ca),vb=total(cb); long difference=Math.abs(va-vb);
        var result=new LinkedHashMap<String,Object>();
        result.put("estadoA",a);result.put("totalA",va);result.put("estadoB",b);result.put("totalB",vb);
        result.put("diferencaAbsoluta",difference);result.put("maior",va==vb?"empate":va>vb?a:b);
        result.put("consultadoEmA",ca.getOrDefault("consultadoEm","não informado"));
        result.put("consultadoEmB",cb.getOrDefault("consultadoEm","não informado"));
        result.put("baseTemporal",refresh?"atualizada nesta pergunta":"contagens documentadas da conversa; não representam nova medição");
        if(denominator!=null) {
            long divisor=denominator.equals(a)?va:vb;
            result.put("denominador",denominator);
            if(divisor==0) result.put("limitacao","Percentual indefinido: o denominador é zero.");
            else result.put("percentual",BigDecimal.valueOf(difference).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(divisor),4,RoundingMode.HALF_UP));
        }
        var historicalSql=new ArrayList<String>();
        var currentSql=new ArrayList<String>();
        for(var count:List.of(ca,cb)) {
            (Boolean.TRUE.equals(count.get("_historical"))?historicalSql:currentSql).addAll(AgentSqlEvidence.queries(count));
        }
        var queries=new ArrayList<>(AgentSqlEvidence.queries(ca));queries.addAll(AgentSqlEvidence.queries(cb));
        var output=AgentSqlEvidence.attach(Map.of("origem","PostgreSQL DataTerra","operacao","COMPARAR","consulta",Map.of("estadoA",a,"estadoB",b),"resultado",result),queries);
        output.put("_sqlHistorico",historicalSql);output.put("_sqlAtual",currentSql);
        return output;
    }
    private Map<String,Object> count(String state,List<Map<String,Object>> evidence,boolean refresh) {
        if(!refresh) for(int i=evidence.size()-1;i>=0;i--) {
            var item=evidence.get(i);
            if("CONTAR".equals(item.get("operacao")) && item.get("consulta") instanceof Map<?,?> filters
                && filters.size()==1 && state.equalsIgnoreCase(String.valueOf(filters.get("estado")))) return item;
        }
        try(var capture=AgentSqlCapture.iniciar()) {
            var result=AgentSqlEvidence.attach(database.executar(Map.of("operacao","CONTAR","estado",state)),capture.consultas());
            evidence.add(result);return result;
        }
    }
    private static long total(Map<String,Object> item) {
        if(item.get("resultado") instanceof Map<?,?> r && r.get("total") instanceof Number n && n.longValue()>=0) return n.longValue();
        throw new IllegalArgumentException("Contagem de banco indisponível.");
    }
    private static String state(Map<?,?> args,String key) {
        if(!(args.get(key) instanceof String s) || s.length()>100) throw new IllegalArgumentException("Estado obrigatório: "+key);
        return TextNormalization.state(s);
    }
}
