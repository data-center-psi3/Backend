package data_centers.brasil.agent.visualization;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import java.math.*;
import static data_centers.brasil.agent.visualization.AgentDatasetService.Dataset;

/** Validates a declarative plan and binds it to server-owned values. No executable grammar. */
@Service
public class AgentVisualizationService {
    private final AgentDatasetService datasets;
    private final JsonMapper json=JsonMapper.builder().build();
    @Value("${agent.visualization.max-records:2000}") private int maxRecords=2000;
    @Value("${agent.visualization.max-bytes:1000000}") private int maxBytes=1000000;
    @Value("${agent.visualization.max-series:20}") private int maxSeries=20;
    private static final Set<String> TYPES=Set.copyOf(ChartType.identifiers());
    private static final Set<String> KEYS=Set.of("datasetId","type","chartType","title","description","x","y","series","yFields","format","unit","sortField","sortDirection","groupBy");
    public AgentVisualizationService(AgentDatasetService datasets) { this.datasets=datasets; }

    public Map<String,Object> build(List<Map<String,Object>> evidence,Map<?,?> proposal,VisualizationIntent intent) {
        var available=datasets.datasets(evidence);
        if(available.isEmpty()) throw new IllegalArgumentException("Nenhum conjunto autorizado disponível para visualização.");
        if(!KEYS.containsAll(proposal.keySet())) throw new IllegalArgumentException("Configuração de visualização não autorizada.");
        Dataset d=available.getLast();
        String id=text(proposal,"datasetId",d.id());
        d=available.stream().filter(s->s.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Conjunto de dados inexistente ou não autorizado."));
        if(proposal.containsKey("type") && !Set.of("table","chart").contains(text(proposal,"type",""))) throw new IllegalArgumentException("Representação inválida.");
        if(proposal.containsKey("chartType")) ChartType.normalize(text(proposal,"chartType",""));
        for(String field:List.of("x","y","series")) if(proposal.containsKey(field) && !text(proposal,field,"").isEmpty()) require(d,text(proposal,field,""),field.equals("y")?"quantitative":field.equals("series")?"nominal":null);
        String type=intent.requested()?intent.type():text(proposal,"type",intent.type());
        if(!Set.of("table","chart").contains(type)) throw new IllegalArgumentException("Representação inválida.");
        String title=text(proposal,"title","Dados consultados no DataTerra");
        String description=text(proposal,"description","");
        var spec=new LinkedHashMap<String,Object>();
        spec.put("schemaVersion","1.0");spec.put("type",type);spec.put("title",title);spec.put("description",description);spec.put("datasetId",d.id());
        var metadata=new LinkedHashMap<String,Object>();
        metadata.put("totalRecords",d.total());metadata.put("returnedRecords",d.data().size());metadata.put("complete",d.complete());
        metadata.put("totalKnown",d.total()>=0);
        metadata.put("source","PostgreSQL DataTerra");metadata.put("queries",d.queries().stream().map(this::provenance).toList());
        metadata.put("status","ok");metadata.put("ordering","source");metadata.put("nullPolicy","preserve; frontend must not coerce null to zero");
        metadata.put("availableDatasets",available.stream().map(datasets::describe).toList());
        metadata.put("dataEndpoint","/api/agent/conversations/{conversationId}/datasets/"+d.id()+"?page=0&size=20");
        spec.put("metadata",metadata);spec.put("data",d.data());
        // Legacy aliases retained for existing chart consumers.
        spec.put("totalElements",d.total());spec.put("complete",d.complete());
        spec.put("sort",List.of());
        if(proposal.containsKey("sortField")) {
            String sortField=text(proposal,"sortField","");require(d,sortField,null);
            String direction=text(proposal,"sortDirection","ascending");
            if(!Set.of("ascending","descending").contains(direction)) throw new IllegalArgumentException("Ordenação inválida.");
            spec.put("sort",List.of(Map.of("field",sortField,"direction",direction)));
        } else if(proposal.containsKey("sortDirection")) throw new IllegalArgumentException("Ordenação exige campo.");
        var grouping=new ArrayList<String>();
        if(proposal.containsKey("groupBy")) {
            if(!(proposal.get("groupBy") instanceof List<?> list) || list.size()>5) throw new IllegalArgumentException("Agrupamento inválido.");
            for(Object field:list) { if(!(field instanceof String s)) throw new IllegalArgumentException("Campo de agrupamento inválido."); require(d,s,null);grouping.add(s); }
        }
        spec.put("groupBy",grouping);metadata.put("aggregation","precomputed_by_authorized_query");
        if(d.data().size()>Math.max(1,maxRecords) || json.writeValueAsBytes(d.data()).length>Math.max(1024,maxBytes/4)) {
            spec.put("data",List.of());spec.put("rows",List.of());metadata.put("complete",false);spec.put("complete",false);
            metadata.put("status","limit_exceeded");metadata.put("returnedRecords",0);
            metadata.put("message","Conjunto excede o limite da resposta. Recupere páginas pelo endpoint de dados da conversa.");
            return spec;
        }
        var columns=d.fields().entrySet().stream().filter(f->!f.getValue().equals("unsupported"))
            .map(f->Map.of("field",f.getKey(),"title",f.getKey(),"type",f.getValue())).toList();
        spec.put("columns",columns);
        if(type.equals("table")) {
            spec.put("rows",d.data());
            var preferences=new LinkedHashMap<String,Object>();
            for(String key:List.of("x","y","series","chartType","yFields","sortField","sortDirection","groupBy")) if(proposal.containsKey(key)) preferences.put(key,proposal.get(key));
            spec.put("preferredPresentation",preferences);return bounded(spec);
        }
        if(d.data().isEmpty()) {
            spec.put("type","table");spec.put("rows",List.of());metadata.put("status",d.total()==0?"empty":"no_data");
            metadata.put("message",d.total()==0?"Nenhum registro encontrado com os filtros consultados.":"Dados precisam ser recuperados novamente a partir da consulta preservada.");return spec;
        }
        String preferredType=Set.of("line","area").contains(intent.chartType()==null?text(proposal,"chartType",""):intent.chartType())?"temporal":
            "scatter".equals(intent.chartType()==null?text(proposal,"chartType",""):intent.chartType())?"quantitative":"nominal";
        String x=text(proposal,"x",preferred(d,preferredType));
        String y=text(proposal,"y",preferred(d,"quantitative"));
        String series=text(proposal,"series","");
        var metrics=new ArrayList<String>();
        if(proposal.containsKey("yFields")) {
            if(!(proposal.get("yFields") instanceof List<?> list) || list.isEmpty() || list.size()>maxSeries) throw new IllegalArgumentException("Quantidade de séries inválida.");
            for(Object field:list) { if(!(field instanceof String name)) throw new IllegalArgumentException("Campo de série inválido."); require(d,name,"quantitative");metrics.add(name); }
        }
        if(!series.isEmpty()) {
            require(d,series,"nominal");
            if(d.data().stream().map(r->r.get(series)).distinct().count()>maxSeries) throw new IllegalArgumentException("Quantidade máxima de séries excedida.");
        }
        String chosen=intent.chartType()!=null?ChartType.normalize(intent.chartType()):proposal.containsKey("chartType")?ChartType.normalize(text(proposal,"chartType","")):"";
        if(chosen.isEmpty()) {
            if(intent.percentage() && d.data().size()<=12) chosen="pie";
            else if(d.fields().getOrDefault(x,"").equals("temporal")) chosen="line";
            else if(d.fields().getOrDefault(x,"").equals("quantitative")) chosen="scatter";
            else chosen=d.data().size()>20?"horizontalBar":"bar";
        }
        if(!TYPES.contains(chosen)) throw new IllegalArgumentException("Tipo de gráfico não permitido.");
        require(d,x,null);require(d,y,"quantitative");
        if(Set.of("line","area").contains(chosen) && !"temporal".equals(d.fields().get(x)))
            throw new IllegalArgumentException("Não há um campo temporal válido selecionado. Não é possível inferir uma evolução histórica das instalações a partir destes dados.");
        if(chosen.equals("scatter")) require(d,x,"quantitative");
        if(Set.of("bar","horizontalBar","pie","donut").contains(chosen) && !Set.of("nominal","temporal").contains(d.fields().get(x)))
            throw new IllegalArgumentException("Este gráfico exige uma dimensão categórica ou temporal.");
        if(ChartType.circular(chosen)) {
            if(!series.isEmpty() || !metrics.isEmpty()) throw new IllegalArgumentException("Pizza/rosca não suporta múltiplas séries.");
            if(d.data().size()>12) metadata.put("warning","Muitas categorias podem dificultar a leitura. O tipo solicitado e todos os registros foram preservados; barras são uma alternativa no frontend.");
            BigDecimal sum=BigDecimal.ZERO;
            for(var row:d.data()) if(row.get(y) instanceof Number n) {
                var value=new BigDecimal(n.toString());if(value.signum()<0) throw new IllegalArgumentException("Participação exige valores não negativos.");sum=sum.add(value);
            }
            if(sum.signum()==0) throw new IllegalArgumentException("Não há valores positivos para calcular participação.");
            metadata.put("percentageDenominator",sum);metadata.put("percentageScope",d.complete()?"full_dataset":"returned_records_only");
        }
        if(d.fields().containsKey("unidade") && d.data().stream().map(r->r.get("unidade")).filter(Objects::nonNull).distinct().count()>1)
            throw new IllegalArgumentException("Há unidades diferentes no conjunto. Consulte indicadores da mesma unidade para compará-los.");
        if(intent.percentage() && !metadata.containsKey("percentageDenominator")) {
            BigDecimal sum=BigDecimal.ZERO;
            for(var row:d.data()) if(row.get(y) instanceof Number n) {
                var value=new BigDecimal(n.toString());if(value.signum()<0) throw new IllegalArgumentException("Participação exige valores não negativos.");sum=sum.add(value);
            }
            if(sum.signum()==0) throw new IllegalArgumentException("Não há valores positivos para calcular participação.");
            metadata.put("percentageDenominator",sum);metadata.put("percentageScope",d.complete()?"full_dataset":"returned_records_only");
        }
        var encoding=new LinkedHashMap<String,Object>();
        var xAxis=axis(d,x,proposal); var yAxis=new LinkedHashMap<String,Object>(axis(d,y,proposal));
        if(!metrics.isEmpty()) yAxis.put("fields",List.copyOf(metrics));
        if(intent.percentage()) { yAxis.put("normalization","percent");yAxis.put("denominator",metadata.get("percentageDenominator")); }
        encoding.put("x",xAxis);encoding.put("y",yAxis);
        if(!series.isEmpty()) encoding.put("color",axis(d,series,Map.of()));
        if(ChartType.circular(chosen)) {
            encoding.put("color",xAxis);encoding.put("theta",yAxis);
        }
        encoding.put("tooltip",columns);
        spec.put("encoding",encoding);spec.put("chartType",chosen);spec.put("dimension",x);spec.put("metric",y);
        spec.put("legend",Map.of("visible",ChartType.circular(chosen) || !series.isEmpty() || !metrics.isEmpty(),"field",ChartType.circular(chosen)?x:series));
        return bounded(spec);
    }
    private Map<String,Object> bounded(Map<String,Object> spec) {
        if(json.writeValueAsBytes(spec).length*2L+2048>Math.max(4096,maxBytes)) {
            spec.put("data",List.of());spec.put("rows",List.of());spec.put("complete",false);
            var meta=metadata(spec);meta.put("complete",false);meta.put("returnedRecords",0);meta.put("status","limit_exceeded");meta.put("message","Resposta excede o limite configurado. Recupere páginas pelo endpoint de dados da conversa.");
        }
        return spec;
    }
    public Map<String,Object> previousPlan(List<Map<String,Object>> evidence,VisualizationIntent intent) {
        var plan=new LinkedHashMap<String,Object>();
        for(var e:evidence) if(e.get("_visualization") instanceof Map<?,?> spec) {
            plan.clear();
            if(spec.get("preferredPresentation") instanceof Map<?,?> preferred) preferred.forEach((k,v)->{ if(k instanceof String key && KEYS.contains(key)) plan.put(key,v); });
            for(String key:List.of("datasetId","title","description","chartType")) if(spec.get(key) instanceof String s) plan.put(key,s);
            if(spec.get("encoding") instanceof Map<?,?> encoding) {
                for(String axis:List.of("x","y","color")) if(encoding.get(axis) instanceof Map<?,?> m && m.get("field") instanceof String field) {
                    if(axis.equals("color") && spec.get("chartType") instanceof String chart && ChartType.circular(chart)) continue;
                    plan.put(axis.equals("color")?"series":axis,field);
                    if(axis.equals("y") && m.get("fields") instanceof List<?> metrics) plan.put("yFields",metrics);
                }
            }
        }
        plan.put("type",intent.type());return plan;
    }
    public Map<String,Object> fallback(List<Map<String,Object>> evidence,VisualizationIntent intent,String warning) {
        if(datasets.datasets(evidence).isEmpty()) return Map.of("schemaVersion","1.0","type","table","data",List.of(),"rows",List.of(),"metadata",Map.of("status","no_data","message","Nenhum conjunto autorizado disponível. Consulte os dados antes de gerar a visualização.","returnedRecords",0,"complete",false));
        try {
            var result=build(evidence,Map.of(),intent);
            if(!warning.isEmpty()) metadata(result).put("warning",warning);
            return result;
        } catch(IllegalArgumentException e) {
            var result=build(evidence,Map.of("type","table"),new VisualizationIntent(true,false,"table",null,false));
            metadata(result).put("status","unsupported");metadata(result).put("message",e.getMessage()+" Os dados reais estão disponíveis em tabela.");
            return result;
        }
    }
    @SuppressWarnings("unchecked") private Map<String,Object> metadata(Map<String,Object> spec) { return (Map<String,Object>)spec.get("metadata"); }
    private Map<String,Object> provenance(Map<String,Object> e) {
        return Map.of("operation",e.get("operacao"),"filters",e.getOrDefault("consulta",Map.of()),"queriedAt",e.getOrDefault("consultadoEm","não informado"),"historical",Boolean.TRUE.equals(e.get("_historical")),"request",e.getOrDefault("_request",Map.of()));
    }
    private Map<String,Object> axis(Dataset d,String field,Map<?,?> proposal) {
        var result=new LinkedHashMap<String,Object>();result.put("field",field);result.put("type",d.fields().get(field));result.put("title",field);
        if(d.fields().get(field).equals("quantitative")) {
            String format=text(proposal,"format","");
            if(!Set.of("","integer","decimal","percent").contains(format)) throw new IllegalArgumentException("Formato numérico inválido.");
            String unit=text(proposal,"unit","");
            String storedUnit=d.data().stream().map(r->r.get("unidade")).filter(String.class::isInstance).map(String.class::cast).findFirst().orElse(field.endsWith("Mw")?"MW":field.endsWith("M2")?"m²":"");
            if(!unit.isEmpty() && !unit.equals(storedUnit)) throw new IllegalArgumentException("Unidade não confirmada nos dados.");
            result.put("format",format);result.put("unit",storedUnit);
        }
        return result;
    }
    private String preferred(Dataset d,String type) {
        if(type.equals("quantitative")) for(String metric:List.of("quantidade","valor","total")) if("quantitative".equals(d.fields().get(metric))) return metric;
        return d.fields().entrySet().stream().filter(f->f.getValue().equals(type)).map(Map.Entry::getKey).findFirst()
            .orElseGet(()->d.fields().entrySet().stream().filter(f->!f.getValue().equals("unsupported") && !f.getValue().equals("unknown")).map(Map.Entry::getKey).findFirst().orElse(""));
    }
    private void require(Dataset d,String field,String type) {
        String actual=d.fields().get(field);
        if(actual==null || actual.equals("unknown") || actual.equals("unsupported")) throw new IllegalArgumentException("Campo inexistente ou sem valores utilizáveis: "+field);
        if(type!=null && !type.equals(actual)) throw new IllegalArgumentException("O campo "+field+" exige tipo "+type+"; disponível: "+actual+".");
        if("quantitative".equals(actual) && d.data().stream().map(r->r.get(field)).filter(Number.class::isInstance).map(Number.class::cast).anyMatch(n->!Double.isFinite(n.doubleValue())))
            throw new IllegalArgumentException("Métrica contém valor numérico não finito.");
    }
    private static String text(Map<?,?> args,String key,String fallback) {
        if(!args.containsKey(key)) return fallback;
        if(!(args.get(key) instanceof String s) || s.length()>300 || s.matches("(?s).*[<>\\p{Cntrl}].*")) throw new IllegalArgumentException("Texto inválido: "+key);
        return s;
    }
}
