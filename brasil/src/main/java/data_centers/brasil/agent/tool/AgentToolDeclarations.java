package data_centers.brasil.agent.tool;

import data_centers.brasil.agent.service.AgentDataBaseService;
import data_centers.brasil.agent.visualization.ChartType;
import org.springframework.stereotype.Component;
import java.util.*;

/** Tool names and JSON schemas presented to Gemini. Query execution stays in the database service. */
@Component
public class AgentToolDeclarations {
    public List<Map<String, Object>> declarations() {
        var properties = new LinkedHashMap<String, Object>();
        properties.put("todos",Map.of("type","BOOLEAN","description","MUNICIPIOS: recupera automaticamente todas as páginas dentro dos limites de segurança. Use para todos, lista completa ou sem omissões."));
        properties.put("ranking",Map.of("type","BOOLEAN","description","MUNICIPIOS: ordena por quantidade decrescente. Para top 10 use limite=10 e todos=false."));
        properties.put("operacao", Map.of("type", "STRING", "enum", Arrays.stream(AgentDataBaseService.Operacao.values()).map(Enum::name).toList()));
        for (String name : List.of("estado", "cidade", "empresa", "nome", "termo")) properties.put(name, Map.of("type", "STRING"));
        for (String name : List.of("dataCenterId", "fonteId", "pagina", "limite")) properties.put(name, Map.of("type", "INTEGER"));
        return List.of(
            visualizationDeclaration(),
            Map.of("name", "consultar_dataterra", "description", "Consulta autorizada ao PostgreSQL. Contagens e agregações reais; pagina inicia em 0; limite 1 a 50. MUNICIPIOS retorna município, estado e quantidade de instalações; todos=true reúne páginas; ranking=true ordena por quantidade decrescente. Para top 10 municípios use MUNICIPIOS, ranking=true, limite=10, todos=false. Filtros estado/cidade/empresa/nome para CONTAR/LISTAR/OPERADORES/MUNICIPIOS. dataCenterId para LISTAR/INDICADORES/BENEFICIOS/PESQUISAS/FONTES; fonteId só FONTES; termo para INDICADORES/BENEFICIOS/PESQUISAS/NOTICIAS. Sem SQL.",
                "parameters", Map.of("type", "OBJECT", "properties", properties, "required", List.of("operacao"))),
            Map.of("name", "resposta_conceitual", "description", "Somente para perguntas gerais e conceituais sem fatos sobre a base DataTerra.",
                "parameters", Map.of("type", "OBJECT", "properties", Map.of())),
            Map.of("name","comparar_contagens","description","Compare dois estados usando contagens reais da conversa; calcula maior, diferença absoluta e percentual com denominador explícito. atualizar=true refaz as contagens; caso contrário usa o histórico documentado quando disponível.",
                "parameters",Map.of("type","OBJECT","properties",Map.of(
                    "estadoA",Map.of("type","STRING"),"estadoB",Map.of("type","STRING"),
                    "percentualSobre",Map.of("type","STRING"),"atualizar",Map.of("type","BOOLEAN")),"required",List.of("estadoA","estadoB"))));
    }
    private Map<String,Object> visualizationDeclaration() {
        var properties=new LinkedHashMap<String,Object>();
        for(String key:List.of("datasetId","title","description","x","y","series","unit","sortField")) properties.put(key,Map.of("type","STRING"));
        properties.put("sortDirection",Map.of("type","STRING","enum",List.of("ascending","descending")));
        properties.put("groupBy",Map.of("type","ARRAY","items",Map.of("type","STRING")));
        properties.put("type",Map.of("type","STRING","enum",List.of("chart","table")));
        properties.put("chartType",Map.of("type","STRING","enum",ChartType.identifiers()));
        properties.put("format",Map.of("type","STRING","enum",List.of("integer","decimal","percent")));
        properties.put("yFields",Map.of("type","ARRAY","items",Map.of("type","STRING")));
        return Map.of("name","planejar_visualizacao","description","Planeja tabela ou gráfico usando SOMENTE campos do esquema de um dataset autorizado já consultado. Prioridade: tipo explícito do usuário, interpretação semântica atual, recomendação automática. Pie/pizza=pie, rosca/donut/doughnut=donut, horizontal bar=barras horizontais=horizontalBar. Muitas categorias geram aviso, não troca de tipo. Em conversões sem nova consulta, use datasetId anterior inclusive para pedidos sem palavras-chave. Nunca envie valores, SQL, HTML, JS ou novas séries. x/y são campos existentes; series é categórico; yFields permite métricas da mesma unidade. Linha exige campo temporal verdadeiro. Não reduza nem filtre dados para mudar formato.",
            "parameters",Map.of("type","OBJECT","properties",properties,"required",List.of("type")));
    }
}
