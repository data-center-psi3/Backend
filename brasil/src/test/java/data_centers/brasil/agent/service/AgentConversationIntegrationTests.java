package data_centers.brasil.agent.service;

import data_centers.brasil.agent.memory.*;
import data_centers.brasil.agent.client.GeminiClient;
import data_centers.brasil.agent.config.AgentDebugSettings;
import data_centers.brasil.agent.formatter.AgentFallbackFormatter;
import data_centers.brasil.agent.controller.AgentController;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"gemini.api.key=test","spring.datasource.url=jdbc:h2:mem:agent-conversations;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"})
@ActiveProfiles("test") @Transactional
class AgentConversationIntegrationTests {
    @Autowired AgentDataBaseService database;
    @Autowired AgentConversationService memory;
    @Autowired AgentConversationStore store;
    @Autowired JdbcTemplate jdbc;
    GeminiClient client;
    AgentService agent;
    List<List<Object>> sent;
    JsonMapper json=JsonMapper.builder().build();
    @BeforeEach void setup() {
        for(int i=0;i<5;i++) jdbc.update("INSERT INTO data_center(nome,cidade,estado) VALUES (?,'Sao Paulo','SP')","SP "+i);
        for(int i=0;i<2;i++) jdbc.update("INSERT INTO data_center(nome,cidade,estado) VALUES (?,'Rio de Janeiro','RJ')","RJ "+i);
        client=mock(GeminiClient.class);sent=new ArrayList<>();
        var debug=mock(AgentDebugSettings.class);when(debug.sqlAllowed()).thenReturn(true);
        agent=new AgentService(client,database,new AgentFallbackFormatter(),memory,debug,new AgentToolTelemetry(),new AgentComparisonService(database));
    }
    private Map<String,Object> call(String tool,Map<String,Object> args) {
        return Map.of("role","model","parts",List.of(Map.of("functionCall",Map.of("name",tool,"args",args))));
    }
    private Map<String,Object> answer(String text) { return Map.of("role","model","parts",List.of(Map.of("text",text))); }
    @SafeVarargs private final void responses(Map<String,Object>... responses) {
        reset(client);var queue=new ArrayDeque<>(List.of(responses));
        when(client.generate(anyList(),anyString())).thenAnswer(invocation->{
            List<Object> history=invocation.getArgument(0);sent.add(new ArrayList<>(history));return queue.removeFirst();
        });
    }
    @SuppressWarnings("unchecked")
    private Map<String,Object> latestToolResult() {
        var history=sent.getLast(); var last=(Map<?,?>)history.getLast();
        var part=(Map<?,?>)((List<?>)last.get("parts")).getFirst();
        return (Map<String,Object>)((Map<?,?>)part.get("functionResponse")).get("response");
    }
    @Test void sessionsAreListedAndReadOnlyForTheirOwner() {
        var alice=memory.open(null,"alice");
        memory.save(alice,"Pergunta da Alice","Resposta da Alice","fonte",List.of());
        var bob=memory.open(null,"bob");
        memory.save(bob,"Pergunta do Bob","Resposta do Bob","fonte",List.of());
        memory.open(null,"alice"); // Empty sessions are not listed.
        var listed=memory.list("alice");
        assertEquals(1,listed.size());
        assertEquals(alice.id(),listed.getFirst().id());
        assertEquals("Pergunta da Alice",listed.getFirst().title());
        var detail=memory.read(alice.id(),"alice");
        assertEquals(List.of(new AgentConversationService.Message("user","Pergunta da Alice"),new AgentConversationService.Message("assistant","Resposta da Alice")),detail.messages());
        assertThrows(ResponseStatusException.class,()->memory.read(bob.id(),"alice"));
        assertThrows(ResponseStatusException.class,()->memory.list(null));
        jdbc.update("UPDATE agent_conversation SET updated_at=? WHERE id=?",java.sql.Timestamp.from(java.time.Instant.now().minus(java.time.Duration.ofDays(91))),alice.id());
        assertTrue(memory.read(alice.id(),"alice").expired());
        assertTrue(memory.list("alice").getFirst().expired());
    }
    @Test void requestedFiveMessageSequenceUsesPersistentCountsAndCorrectDenominator() {
        responses(call("consultar_dataterra",Map.of("operacao","CONTAR","estado","São Paulo")),answer("São Paulo: 5 cadastrados."));
        var first=agent.perguntarComFonte("Quantos data centers existem em São Paulo?",null,"alice");
        UUID id=first.conversationId();assertNotNull(id);assertFalse(first.consulta().isEmpty());
        // A fresh service instance reads the database; it has no previous local state.
        var reloaded=new AgentConversationService(store,8,60000,7);
        assertEquals(1,reloaded.open(id,"alice").turns().size());
        responses(call("consultar_dataterra",Map.of("operacao","CONTAR","estado","Rio de Janeiro")),answer("Rio de Janeiro: 2 cadastrados."));
        var second=agent.perguntarComFonte("E no Rio de Janeiro?",id,"alice");assertEquals(id,second.conversationId());
        var originalSql=new ArrayList<>(first.consulta());originalSql.addAll(second.consulta());
        String history=json.writeValueAsString(sent.get(sent.size()-2));
        assertTrue(history.contains("São Paulo"));assertTrue(history.contains("CONTAR"));assertTrue(history.contains("consultadoEm"));
        String[] questions={"Qual dos dois estados possui mais?","E qual a diferença entre eles?","Qual a porcentagem dessa diferença em relação ao Rio de Janeiro?"};
        for(int i=0;i<questions.length;i++) {
            var args=new HashMap<String,Object>(Map.of("estadoA","SP","estadoB","RJ"));
            if(i==2) args.put("percentualSobre","RJ");
            responses(call("comparar_contagens",args),answer(i==2?"A diferença é 150% do RJ, com base nas consultas anteriores.":"SP: 5; RJ: 2. Diferença: 3, conforme histórico."));
            var response=agent.perguntarComFonte(questions[i],id,"alice");
            var result=(Map<?,?>)latestToolResult().get("resultado");
            assertEquals(5L,((Number)result.get("totalA")).longValue());assertEquals(2L,((Number)result.get("totalB")).longValue());
            assertEquals(3L,((Number)result.get("diferencaAbsoluta")).longValue());assertEquals("SP",result.get("maior"));
            assertEquals(originalSql,response.consulta(),"Reuse must preserve the exact SQL associated with both counts");
            assertEquals("historico",response.consultaOrigem());
            assertEquals(id,response.conversationId());assertEquals("banco_de_dados",response.fonte());
            if(i==2) { assertEquals("RJ",result.get("denominador"));assertEquals(150.0,((Number)result.get("percentual")).doubleValue()); }
        }
        assertEquals(5,memory.open(id,"alice").turns().size());
    }
    @Test void refreshQueriesAgainAndNeverAcceptsModelProvidedNumbers() {
        var results=new ArrayList<Map<String,Object>>();
        results.add(database.executar(Map.of("operacao","CONTAR","estado","SP")));
        results.add(database.executar(Map.of("operacao","CONTAR","estado","RJ")));
        jdbc.update("INSERT INTO data_center(nome,cidade,estado) VALUES ('Novo','Nova','RJ')");
        var comparison=new AgentComparisonService(database);
        var refreshed=comparison.executar(Map.of("estadoA","SP","estadoB","RJ","atualizar",true,"percentualSobre","RJ"),results);
        var r=(Map<?,?>)refreshed.get("resultado");assertEquals(3L,((Number)r.get("totalB")).longValue());
        assertEquals(2L,((Number)r.get("diferencaAbsoluta")).longValue());
        assertEquals(66.6667,((Number)r.get("percentual")).doubleValue());
        assertThrows(IllegalArgumentException.class,()->comparison.executar(Map.of("estadoA","SP","estadoB","RJ","totalA",999),results));
    }
    @Test void ownershipNewSessionsExpirationAndConflictsAreEnforced() {
        var one=memory.open(null,"alice");var two=memory.open(null,"alice");
        assertNotEquals(one.id(),two.id());
        assertEquals(404,assertThrows(ResponseStatusException.class,()->memory.open(one.id(),"bob")).getStatusCode().value());
        assertEquals(404,assertThrows(ResponseStatusException.class,()->memory.open(UUID.randomUUID(),"alice")).getStatusCode().value());
        var stale=memory.open(one.id(),"alice");
        memory.save(one,"Pergunta","Resposta","conhecimento_geral_gemini",List.of());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->memory.save(stale,"Outra","Outra","conhecimento_geral_gemini",List.of())).getStatusCode().value());
        assertTrue(memory.open(two.id(),"alice").turns().isEmpty());
        jdbc.update("UPDATE agent_conversation SET updated_at=? WHERE id=?",java.sql.Timestamp.from(java.time.Instant.now().minus(java.time.Duration.ofDays(8))),two.id());
        assertEquals(404,assertThrows(ResponseStatusException.class,()->memory.open(two.id(),"alice")).getStatusCode().value());
    }
    @Test void historyIsBoundedAndClarificationIsAllowedWithoutContext() {
        var bounded=new AgentConversationService(store,2,8000,7);var session=bounded.open(null,"alice");
        for(int i=0;i<5;i++) { bounded.save(session,"Pergunta "+i,"Resposta "+i,"conhecimento_geral_gemini",List.of());session=bounded.open(session.id(),"alice"); }
        assertEquals(2,session.turns().size());assertEquals(4,bounded.history(session).size());
        assertFalse(json.writeValueAsString(bounded.history(session)).contains("Pergunta 0"));
        responses(call("resposta_conceitual",Map.of()),answer("Quais elementos você deseja comparar?"));
        var response=agent.perguntarComFonte("E qual a diferença entre eles?",null,"alice");
        assertTrue(response.response().contains("Quais elementos"));assertNotNull(response.conversationId());
    }
    @Test void controllerReturnsConversationIdAndAcceptsItOnNextRequest() throws Exception {
        responses(call("resposta_conceitual",Map.of()),answer("Conceito geral."),call("resposta_conceitual",Map.of()),answer("Continuação."));
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new AgentController(agent)).build();
        var first=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/agent/chat")
            .principal(()->"alice").contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"message\":\"Explique PUE\"}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn();
        String id=json.readTree(first.getResponse().getContentAsString()).get("conversationId").asString();
        assertNotNull(UUID.fromString(id));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/agent/chat")
            .principal(()->"alice").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .content("{\"message\":\"E WUE?\",\"conversationId\":\""+id+"\"}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.conversationId").value(id));
    }    @Test void sqlIsHiddenWithoutExplicitDevelopmentAuthorization() {
        var noDebug=new AgentService(client,database,new AgentFallbackFormatter(),memory,mock(AgentDebugSettings.class),new AgentToolTelemetry(),new AgentComparisonService(database));
        responses(call("consultar_dataterra",Map.of("operacao","CONTAR")),answer("7 cadastrados."));
        var response=noDebug.perguntarComFonte("Quantos cadastrados?",null,"alice");
        assertNotNull(response.conversationId());assertTrue(response.consulta().isEmpty());
    }
    @Test void unavailableGeminiStillReturnsConversationIdInControlledHttpError() throws Exception {
        when(client.generate(anyList(),anyString())).thenThrow(new org.springframework.web.client.ResourceAccessException("secret connection data"));
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new AgentController(agent))
            .setControllerAdvice(new data_centers.brasil.agent.controller.AgentExceptionHandler()).build();
        var result=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/agent/chat")
            .principal(()->"alice").contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"message\":\"Quantos existem?\"}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isServiceUnavailable()).andReturn();
        var body=json.readTree(result.getResponse().getContentAsString());
        UUID id=UUID.fromString(body.get("conversationId").asString());
        assertTrue(memory.open(id,"alice").turns().isEmpty());
        assertFalse(result.getResponse().getContentAsString().contains("secret"));
    }
    @Test void unusuallyLargeToolResultsStayWithinPersistedHistoryLimit() {
        var bounded=new AgentConversationService(store,2,8000,7);var session=bounded.open(null,"alice");
        var record=new HashMap<String,Object>();for(int i=0;i<25;i++) record.put("field"+i,"x".repeat(5000));
        bounded.save(session,"q".repeat(4000),"a".repeat(15000),"banco_de_dados",List.of(Map.of("operacao","LISTAR","resultado",List.of(record,record))));
        String stored=jdbc.queryForObject("SELECT history_json FROM agent_conversation WHERE id=?",String.class,session.id());
        assertTrue(stored.length()<=8000,()->"Persisted size: "+stored.length());
        assertFalse(bounded.history(bounded.open(session.id(),"alice")).isEmpty());
    }
    @Test void zeroDenominatorIsExplicitAndNeverInventsPercentage() {
        var results=new ArrayList<Map<String,Object>>();
        var output=new AgentComparisonService(database).executar(Map.of("estadoA","SP","estadoB","AC","percentualSobre","AC"),results);
        var r=(Map<?,?>)output.get("resultado");assertFalse(r.containsKey("percentual"));assertTrue(r.get("limitacao").toString().contains("zero"));
    }

    @Test void historicalSqlSurvivesReloadAndIsNotSentToGemini() {
        responses(call("consultar_dataterra",Map.of("operacao","CONTAR","estado","SP")),answer("SP: 5."));
        var first=agent.perguntarComFonte("Quantos em SP?",null,"alice");
        assertEquals("atual",first.consultaOrigem());
        responses(call("consultar_dataterra",Map.of("operacao","CONTAR","estado","RJ")),answer("RJ: 2."));
        var second=agent.perguntarComFonte("E no RJ?",first.conversationId(),"alice");
        var expected=new ArrayList<>(first.consulta());expected.addAll(second.consulta());

        var reloaded=new AgentConversationService(store,8,60000,7);
        var debug=mock(AgentDebugSettings.class);when(debug.sqlAllowed()).thenReturn(true);
        var restarted=new AgentService(client,database,new AgentFallbackFormatter(),reloaded,debug,new AgentToolTelemetry(),new AgentComparisonService(database));
        // Changed data proves this comparison actually uses its documented historical counts.
        jdbc.update("INSERT INTO data_center(nome,cidade,estado) VALUES ('Posterior','Rio de Janeiro','RJ')");
        responses(call("comparar_contagens",Map.of("estadoA","SP","estadoB","RJ")),answer("Diferença histórica: 3."));
        var result=restarted.perguntarComFonte("Qual a diferença?",first.conversationId(),"alice");
        assertEquals(expected,result.consulta());assertEquals("historico",result.consultaOrigem());
        assertEquals(2,((Number)((Map<?,?>)latestToolResult().get("resultado")).get("totalB")).intValue());
        String modelContext=json.writeValueAsString(sent.getLast());
        assertFalse(modelContext.contains("_sql"));assertFalse(modelContext.contains("select count("));

        var hidden=new AgentService(client,database,new AgentFallbackFormatter(),reloaded,mock(AgentDebugSettings.class),new AgentToolTelemetry(),new AgentComparisonService(database));
        responses(call("comparar_contagens",Map.of("estadoA","SP","estadoB","RJ")),answer("Diferença histórica: 3."));
        var protectedResult=hidden.perguntarComFonte("Repita a comparação",first.conversationId(),"alice");
        assertTrue(protectedResult.consulta().isEmpty());assertEquals("oculta",protectedResult.consultaOrigem());
    }

    @Test void newAndHistoricalCountsAreIdentifiedAsMixedAndRefreshIsCurrent() {
        responses(call("consultar_dataterra",Map.of("operacao","CONTAR","estado","SP")),answer("SP: 5."));
        var first=agent.perguntarComFonte("Quantos em SP?",null,"alice");
        responses(call("comparar_contagens",Map.of("estadoA","SP","estadoB","RJ")),answer("SP: 5; RJ: 2."));
        var mixed=agent.perguntarComFonte("Compare com o RJ",first.conversationId(),"alice");
        assertEquals("mista",mixed.consultaOrigem());assertEquals(2,mixed.consulta().size());
        assertEquals(first.consulta().getFirst(),mixed.consulta().getFirst());
        jdbc.update("INSERT INTO data_center(nome,cidade,estado) VALUES ('Atualizado','Rio de Janeiro','RJ')");
        responses(call("comparar_contagens",Map.of("estadoA","SP","estadoB","RJ","atualizar",true)),answer("Dados atualizados."));
        var refreshed=agent.perguntarComFonte("Atualize a comparação",first.conversationId(),"alice");
        assertEquals("atual",refreshed.consultaOrigem());assertEquals(2,refreshed.consulta().size());
        assertEquals(3,((Number)((Map<?,?>)latestToolResult().get("resultado")).get("totalB")).intValue());
    }

    @Test void longSqlIsStoredExactlyAndLegacyHistoryDoesNotInventQueries() {
        var session=memory.open(null,"alice");
        String rawSql="select count(*) /* "+"x".repeat(2000)+" */ from data_center where estado=?";
        var sp=new HashMap<String,Object>(Map.of("operacao","CONTAR","consulta",Map.of("estado","SP"),"resultado",Map.of("total",5),"_sql",List.of(rawSql)));
        var rj=Map.<String,Object>of("operacao","CONTAR","consulta",Map.of("estado","RJ"),"resultado",Map.of("total",2));
        memory.save(session,"Contagens antigas","SP: 5; RJ: 2.","banco_de_dados",List.of(sp,rj));
        var loaded=memory.results(memory.open(session.id(),"alice"));
        assertEquals(rawSql,((List<?>)loaded.getFirst().get("_sql")).getFirst());
        responses(call("comparar_contagens",Map.of("estadoA","SP","estadoB","RJ")),answer("Diferença: 3."));
        var result=agent.perguntarComFonte("Qual a diferença?",session.id(),"alice");
        assertEquals(List.of(rawSql),result.consulta(),"Only queries that were actually preserved may be returned");
        assertEquals("historico",result.consultaOrigem());
    }

    @Test void fallbackAlsoReturnsSqlOfReusedCounts() {
        responses(call("consultar_dataterra",Map.of("operacao","CONTAR","estado","SP")),answer("SP: 5."));
        var first=agent.perguntarComFonte("Quantos em SP?",null,"alice");
        responses(call("consultar_dataterra",Map.of("operacao","CONTAR","estado","RJ")),answer("RJ: 2."));
        var second=agent.perguntarComFonte("E no RJ?",first.conversationId(),"alice");
        var queue=new ArrayDeque<>(List.of(call("comparar_contagens",Map.of("estadoA","SP","estadoB","RJ"))));
        reset(client);
        when(client.generate(anyList(),anyString())).thenAnswer(invocation->{
            if(queue.isEmpty()) throw new org.springframework.web.client.ResourceAccessException("provider unavailable");
            return queue.removeFirst();
        });
        var result=agent.perguntarComFonte("Qual a diferença?",first.conversationId(),"alice");
        var expected=new ArrayList<>(first.consulta());expected.addAll(second.consulta());
        assertEquals(expected,result.consulta());assertEquals("historico",result.consultaOrigem());
        assertTrue(result.response().contains("Diferença absoluta: 3"));
    }

}
