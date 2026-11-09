package data_centers.brasil.agent.service;

import data_centers.brasil.agent.dto.AgentChatResponse;
import data_centers.brasil.agent.sql.AgentSqlCapture;
import data_centers.brasil.agent.sql.AgentSqlEvidence;
import data_centers.brasil.agent.client.GeminiClient;
import data_centers.brasil.agent.formatter.AgentFallbackFormatter;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import data_centers.brasil.agent.memory.AgentConversationService;
import data_centers.brasil.agent.config.AgentDebugSettings;
import org.springframework.dao.DataAccessException;
import data_centers.brasil.agent.memory.AgentConversationException;
import data_centers.brasil.agent.visualization.*;

/** Orchestrates the conversation and authorized tools; transport and prompt live separately. */
@Service
public class AgentService {
    private final GeminiClient client;
    private final AgentDataBaseService database;
    private final AgentFallbackFormatter formatter;

    private final AgentConversationService memory;
    private final AgentDebugSettings debug;
    private final AgentToolTelemetry telemetry;
    private final AgentComparisonService comparison;
    private final AgentVisualizationService visualizations;
    private final AgentDatasetService datasets;
    private final AgentQueryPaginationService pagination;

    @Autowired
    public AgentService(GeminiClient client,AgentDataBaseService database,AgentFallbackFormatter formatter,
                        AgentConversationService memory,AgentDebugSettings debug,AgentToolTelemetry telemetry,AgentComparisonService comparison,
                        AgentVisualizationService visualizations,AgentDatasetService datasets,AgentQueryPaginationService pagination) {
        this.client=client;this.database=database;this.formatter=formatter;
        this.memory=memory;this.debug=debug;this.telemetry=telemetry;this.comparison=comparison;
        this.visualizations=visualizations;this.datasets=datasets;this.pagination=pagination;
    }
    public AgentService(GeminiClient client,AgentDataBaseService database,AgentFallbackFormatter formatter,
                        AgentConversationService memory,AgentDebugSettings debug,AgentToolTelemetry telemetry,AgentComparisonService comparison) {
        this(client,database,formatter,memory,debug,telemetry,comparison,new AgentVisualizationService(new AgentDatasetService()),new AgentDatasetService(),new AgentQueryPaginationService(database));
    }
    private record Turn(AgentChatResponse response,List<Map<String,Object>> evidence) {}

    public AgentChatResponse perguntarComFonte(String question,UUID conversationId,String owner) {
        validate(question);
        UUID activeId=conversationId;
        try {
            var session=memory.open(conversationId,owner);
            activeId=session.id();
            var turn=run(question,memory.history(session),memory.results(session),session.id());
            memory.save(session,question,turn.response().response(),turn.response().fonte(),turn.evidence());
            var result=turn.response();
            return authorizedSql(result);
        } catch(DataAccessException e) {
            telemetry.record(activeId==null?UUID.randomUUID():activeId,"conversation_memory",Map.of("operacao","MEMORIA"),null,0,false,e);
            throw new AgentConversationException(HttpStatus.SERVICE_UNAVAILABLE,"Memória conversacional temporariamente indisponível. Tente novamente.",activeId);
        } catch(ResponseStatusException e) {
            throw new AgentConversationException(e.getStatusCode(),e.getReason(),activeId);
        }
    }

    public String perguntar(String question) {
        return perguntarComFonte(question).response();
    }

    public AgentChatResponse perguntarComFonte(String question) {
        var result=run(question,List.of(),List.of(),UUID.randomUUID()).response();
        return authorizedSql(result);
    }

    private static void validate(String question) {
        if(question==null || question.isBlank() || question.length()>4000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A mensagem deve conter entre 1 e 4000 caracteres.");
    }

    private Turn run(String question,List<Object> previousHistory,List<Map<String,Object>> previousEvidence,UUID conversationId) {
        validate(question);
        List<Object> history = new ArrayList<>(previousHistory);
        history.add(Map.of("role", "user", "parts", List.of(Map.of("text", question))));
        List<Map<String, Object>> evidence = new ArrayList<>();
        var intent=VisualizationIntent.from(question);
        Map<?,?> plan=Map.of();
        boolean conversion=intent.conversion() && !previousEvidence.isEmpty();
        if(conversion) {
            var prior=datasets.datasets(previousEvidence);
            if(!prior.isEmpty()) {
                String selected=prior.getLast().id();
                for(var item:previousEvidence) if(item.get("_visualization") instanceof Map<?,?> v && v.get("datasetId") instanceof String id) selected=id;
                String chosen=selected;
                var source=prior.stream().filter(d->d.id().equals(chosen)).findFirst().orElse(prior.getLast());
                source.queries().forEach(e->evidence.add(AgentSqlEvidence.historical(e)));
                history.add(Map.of("role","user","parts",List.of(Map.of("text","Conversão de formato: mantenha o conjunto anterior e seus filtros. Esquema: "+datasets.describe(source)))));
            }
        }
        List<String> consultas = new ArrayList<>();
        int calls = 0;
        boolean conceptual = false;
        var reusable=new ArrayList<Map<String,Object>>();
        previousEvidence.forEach(item->reusable.add(AgentSqlEvidence.historical(item)));
        var queryCache=new HashMap<Map<?,?>,Map<String,Object>>();
        boolean historicalSql=false;
        boolean currentSql=false;
        if(conversion && evidence.stream().anyMatch(e->Boolean.TRUE.equals(e.get("_requiresRefresh")))) {
            var last=evidence.getLast();
            if(last.get("_request") instanceof Map<?,?> request) {
                try(var capture=AgentSqlCapture.iniciar()) {
                    var refreshed=AgentSqlEvidence.attach(pagination.execute(request,Boolean.TRUE.equals(last.get("_all")) || Boolean.TRUE.equals(request.get("todos"))),capture.consultas());
                    if(last.containsKey("_visualization")) refreshed.put("_visualization",last.get("_visualization"));
                    if(last.containsKey("_datasetId")) refreshed.put("_datasetId",last.get("_datasetId"));
                    evidence.clear();evidence.add(refreshed);consultas.addAll(capture.consultas());currentSql=!capture.consultas().isEmpty();
                }
            }
        }
        if(conversion && !currentSql) { historicalSql=true;evidence.forEach(e->consultas.addAll(AgentSqlEvidence.queries(e))); }
        if(conversion) plan=visualizations.previousPlan(evidence,intent);
        try {
            for (int turn = 0; turn < 6; turn++) {
                Map<?, ?> content = client.generate(history, turn == 0 ? "ANY" : "AUTO");
                Object raw = content.get("parts");
                if (!(raw instanceof List<?> parts) || parts.isEmpty()) throw new IllegalStateException();
                // Preserve the complete model Content, including opaque thoughtSignature fields.
                history.add(content);
                List<Object> responses = new ArrayList<>();
                StringBuilder answer = new StringBuilder();
                for (Object item : parts) {
                    if (!(item instanceof Map<?, ?> part)) throw new IllegalStateException();
                    if (part.get("functionCall") instanceof Map<?, ?> call) {
                        if (++calls > 12) throw new IllegalStateException();
                        String name = call.get("name") instanceof String s ? s : "";
                        Map<String,Object> result;
                        Map<?,?> loggedArgs=Map.of();
                        long started=System.nanoTime();
                        RuntimeException failure=null;
                        boolean cached=false;
                        try {
                            Object rawArgs=call.containsKey("args")?call.get("args"):Map.of();
                            if(!(rawArgs instanceof Map<?,?> args)) throw new IllegalArgumentException("Argumentos devem ser objeto.");
                            loggedArgs=args;
                            if(name.equals("consultar_dataterra")) {
                                if("MUNICIPIOS".equals(args.get("operacao"))) {
                                    var effective=new LinkedHashMap<Object,Object>(args);
                                    String normalized=data_centers.brasil.filter.TextNormalization.normalize(question);
                                    if(normalized.matches("(?s).*(\\btod[oa]s\\b|lista completa|listagem completa|sem omitir|sem omiss|integral|cada um).*") && !normalized.matches("(?s).*\\b(top|ranking|dez|10)\\b.*")) effective.put("todos",true);
                                    args=effective;
                                }
                                cached=queryCache.containsKey(args);
                                if(conversion && !evidence.isEmpty()) { result=evidence.getLast();cached=true; }
                                else {
                                if(cached) result=queryCache.get(args);
                                else try(var capture=AgentSqlCapture.iniciar()) {
                                    String q=data_centers.brasil.filter.TextNormalization.normalize(question);
                                    boolean explicitLimit=q.matches("(?s).*(\\btop\\s*(?:[0-9]+|cinco|dez)|\\b(?:cinco|dez|vinte|[0-9]+)\\s+(?:estados|municipios|operadores|instalacoes)|pagina\\s*[0-9]+).*");
                                    boolean all=intent.all(question) || intent.requested() && !explicitLimit;
                                    result=AgentSqlEvidence.attach(pagination.execute(args,all),capture.consultas());
                                    consultas.addAll(capture.consultas());
                                    currentSql|=!capture.consultas().isEmpty();
                                    queryCache.put(Map.copyOf(args),result);
                                }
                                evidence.add(result);reusable.add(result);
                                }
                            } else if(name.equals("planejar_visualizacao")) {
                                // Semantic conversions can be proposed by Gemini even without a
                                // lexical alias. Only a dataset from this owner's history is reusable.
                                if(evidence.isEmpty() && args.get("datasetId") instanceof String requestedId) {
                                    var source=datasets.datasets(previousEvidence).stream().filter(d->d.id().equals(requestedId)).findFirst()
                                        .orElseThrow(()->new IllegalArgumentException("Dataset anterior não autorizado ou inexistente."));
                                    if(source.queries().stream().anyMatch(e->Boolean.TRUE.equals(e.get("_requiresRefresh"))))
                                        throw new IllegalArgumentException("O dataset precisa ser consultado novamente com os filtros preservados.");
                                    source.queries().forEach(e->evidence.add(AgentSqlEvidence.historical(e)));
                                    evidence.forEach(e->consultas.addAll(AgentSqlEvidence.queries(e)));
                                    historicalSql=true;conversion=true;plan=visualizations.previousPlan(evidence,intent);
                                }
                                var proposed=new LinkedHashMap<Object,Object>();
                                if(conversion) proposed.putAll(plan);
                                proposed.putAll(args);
                                var spec=visualizations.build(evidence,proposed,intent);
                                plan=Map.copyOf(proposed);
                                result=Map.of("status","validated","specification",spec.entrySet().stream().filter(e->!Set.of("data","rows").contains(e.getKey())).collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue)));
                            } else if(name.equals("comparar_contagens")) {
                                int before=reusable.size();
                                try(var capture=AgentSqlCapture.iniciar()) {
                                    result=comparison.executar(args,reusable);
                                    consultas.addAll(AgentSqlEvidence.queries(result));
                                    historicalSql|=!AgentSqlEvidence.strings(result.get("_sqlHistorico")).isEmpty();
                                    currentSql|=!AgentSqlEvidence.strings(result.get("_sqlAtual")).isEmpty();
                                }
                                cached=reusable.size()==before;
                                evidence.addAll(reusable.subList(before,reusable.size()));evidence.add(result);
                            } else if(name.equals("resposta_conceitual") && args.isEmpty()) {
                                conceptual=true;
                                result=Map.of("orientacao","Responda conceitos gerais ou solicite esclarecimento se o histórico não resolver a referência. Não atribua dados externos à base DataTerra.");
                            } else throw new IllegalArgumentException("Ferramenta não autorizada.");
                        } catch(IllegalArgumentException e) {
                            failure=e;result=Map.of("erro",e.getMessage(),"orientacao","Corrija os argumentos e tente novamente.");
                        } catch(RuntimeException e) {
                            failure=e;result=Map.of("erro","Consulta indisponível. Não invente dados nem conclua que há zero registros.");
                        }
                        telemetry.record(conversationId,name,loggedArgs,result,System.nanoTime()-started,cached,failure);
                        var response = new LinkedHashMap<String, Object>();
                        response.put("name", name);
                        response.put("response", intent.requested() || name.equals("planejar_visualizacao") ? datasets.modelResult(result) : AgentSqlEvidence.publicResult(result));
                        if (call.get("id") instanceof String id) response.put("id", id);
                        responses.add(Map.of("functionResponse", response));
                    } else if (!Boolean.TRUE.equals(part.get("thought")) && part.get("text") instanceof String s) answer.append(s);
                }
                if (!responses.isEmpty()) {
                    history.add(Map.of("role", "user", "parts", responses));
                } else if (!answer.toString().isBlank() && (!evidence.isEmpty() || conceptual)) {
                    return complete(question,new AgentChatResponse(answer.toString(), evidence.isEmpty()
                        ? "conhecimento_geral_gemini"
                        : conceptual ? "banco_de_dados_e_conhecimento_geral_gemini" : "banco_de_dados", consultas,conversationId,sqlOrigin(historicalSql,currentSql)),evidence,plan);
                } else throw new IllegalStateException();
            }
        } catch (RestClientException | IllegalStateException e) {
            telemetry.record(conversationId,"planejar_visualizacao",Map.of(),null,0,false,e);
            // Only results already obtained for this question can be used during an outage.
            if (!evidence.isEmpty()) return complete(question,new AgentChatResponse(fallback(evidence), "banco_de_dados", consultas,conversationId,sqlOrigin(historicalSql,currentSql)),evidence,plan);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "O assistente está temporariamente indisponível. Tente novamente em instantes.");
        }
        if (!evidence.isEmpty()) return complete(question,new AgentChatResponse(fallback(evidence), "banco_de_dados", consultas,conversationId,sqlOrigin(historicalSql,currentSql)),evidence,plan);
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Não foi possível concluir a consulta com segurança.");
    }

    private String fallback(List<Map<String, Object>> evidence) {
        return formatter.formatar(evidence);
    }
    private Turn complete(String question,AgentChatResponse response,List<Map<String,Object>> evidence,Map<?,?> plan) {
        var intent=VisualizationIntent.from(question);
        if(intent.requested() || !plan.isEmpty()) {
            Map<String,Object> spec;
            try { spec=visualizations.build(evidence,plan,intent); }
            catch(IllegalArgumentException e) {
                telemetry.record(response.conversationId(),"planejar_visualizacao",plan,null,0,false,e);
                spec=visualizations.fallback(evidence,intent,e.getMessage());
            }
            var metadata=(Map<?,?>)spec.get("metadata");
            String text=metadata.get("returnedRecords")+" registros na visualização. "+(Boolean.TRUE.equals(metadata.get("complete"))?"Conjunto completo.":"Conjunto parcial.");
            if(spec.get("dimension") instanceof String dimension && dimension.equals("municipio")) text=metadata.get("returnedRecords")+" municípios retornados. "+text;
            if(metadata.get("message") instanceof String message) text+=" "+message;
            if(metadata.get("warning") instanceof String warning) text+=" "+warning;
            boolean historical=evidence.stream().anyMatch(e->Boolean.TRUE.equals(e.get("_historical")));
            text+=historical?" Foram preservados os dados e filtros da consulta anterior.":" A visualização reflete a consulta atual ao DataTerra.";
            var saved=new ArrayList<Map<String,Object>>(evidence);
            if(!saved.isEmpty()) {
                var last=new LinkedHashMap<>(saved.getLast());
                var config=new LinkedHashMap<String,Object>(spec);config.remove("data");config.remove("rows");
                last.put("_visualization",config);saved.set(saved.size()-1,last);
            }
            return new Turn(new AgentChatResponse(text,response.fonte(),response.consulta(),response.conversationId(),response.consultaOrigem(),spec,spec),saved);
        }
        if(evidence.stream().anyMatch(e->"MUNICIPIOS".equals(e.get("operacao")))) response=new AgentChatResponse(formatter.formatar(evidence),response.fonte(),response.consulta(),response.conversationId(),response.consultaOrigem());
        return new Turn(response,evidence);
    }

    private static String sqlOrigin(boolean historical,boolean current) {
        return historical ? current?"mista":"historico" : current?"atual":"nenhuma";
    }

    private AgentChatResponse authorizedSql(AgentChatResponse result) {
        if(debug.sqlAllowed()) return result;
        return new AgentChatResponse(result.response(),result.fonte(),List.of(),result.conversationId(),result.consulta().isEmpty()?"nenhuma":"oculta",result.chartData(),result.visualization());
    }

}
