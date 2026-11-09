package data_centers.brasil.agent.service;

import data_centers.brasil.agent.client.GeminiClient;
import data_centers.brasil.agent.config.AgentDebugSettings;
import data_centers.brasil.agent.formatter.AgentFallbackFormatter;
import data_centers.brasil.agent.memory.*;
import data_centers.brasil.agent.visualization.*;
import data_centers.brasil.agent.controller.*;
import data_centers.brasil.agent.dto.AgentChatResponse;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"gemini.api.key=test","spring.datasource.url=jdbc:h2:mem:visualization-integration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"})
@ActiveProfiles("test") @Transactional
class AgentVisualizationIntegrationTests {
    @Autowired AgentDataBaseService database;
    @Autowired AgentConversationService memory;
    @Autowired AgentConversationStore store;
    @Autowired AgentDatasetService datasets;
    @Autowired AgentVisualizationService visualizations;
    @Autowired AgentQueryPaginationService pagination;
    @Autowired JdbcTemplate jdbc;
    GeminiClient client; AgentService agent;
    @BeforeEach void setup() {
        jdbc.update("DELETE FROM data_center");
        for(int i=0;i<98;i++) jdbc.update("INSERT INTO data_center(nome,cidade,estado) VALUES (?,?,?)","DC "+i,String.format("Cidade %03d",i),i%2==0?"SP":"RJ");
        client=mock(GeminiClient.class);
        agent=new AgentService(client,database,new AgentFallbackFormatter(),memory,mock(AgentDebugSettings.class),new AgentToolTelemetry(),new AgentComparisonService(database),visualizations,datasets,pagination);
    }
    Map<String,Object> call(String name,Map<?,?> args) { return Map.of("role","model","parts",List.of(Map.of("thoughtSignature","signature","functionCall",Map.of("name",name,"args",args,"id","call")))); }
    Map<String,Object> answer() { return Map.of("role","model","parts",List.of(Map.of("text","Resumo do modelo."))); }
    void steps(Object... responses) { reset(client);doReturn(responses[0],Arrays.copyOfRange(responses,1,responses.length)).when(client).generate(anyList(),anyString()); }
    Map<?,?> spec(AgentChatResponse r) { return (Map<?,?>)r.visualization(); }
    @Test void realUfQueryProducesBarThenPieWithSameValues() throws Exception {
        steps(call("consultar_dataterra",Map.of("operacao","ESTADOS")),call("planejar_visualizacao",Map.of("type","chart","x","estado","y","quantidade","chartType","bar")),answer());
        var first=agent.perguntarComFonte("Faça um gráfico de data centers por UF.",null,"alice");
        assertEquals("bar",spec(first).get("chartType"));assertEquals(first.chartData(),first.visualization());
        steps(call("planejar_visualizacao",Map.of("type","chart","chartType","pie")),answer());
        var second=agent.perguntarComFonte("Agora transforme em gráfico de pizza.",first.conversationId(),"alice");
        assertEquals("pie",spec(second).get("chartType"));assertEquals(JsonMapper.builder().build().writeValueAsString(spec(first).get("data")),JsonMapper.builder().build().writeValueAsString(spec(second).get("data")));
        assertTrue(second.response().contains("consulta anterior"));assertTrue(second.consulta().isEmpty());
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/visualization-bar-example.json"),JsonMapper.builder().build().writeValueAsString(first));
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/visualization-pie-example.json"),JsonMapper.builder().build().writeValueAsString(second));
    }
    @Test void listToTableToGraphToTableKeepsAllNinetyEightAndFilters() {
        steps(call("consultar_dataterra",Map.of("operacao","MUNICIPIOS")),answer());
        var first=agent.perguntarComFonte("Liste todos os municípios.",null,"alice");
        steps(call("planejar_visualizacao",Map.of("type","table")),answer());
        var table=agent.perguntarComFonte("Agora organize em colunas.",first.conversationId(),"alice");
        assertEquals("table",spec(table).get("type"));assertEquals(98,((List<?>)spec(table).get("rows")).size());
        // Even an erroneous new query with LIMIT 10 cannot replace the selected snapshot.
        steps(call("consultar_dataterra",Map.of("operacao","MUNICIPIOS","limite",10)),call("planejar_visualizacao",Map.of("type","chart")),answer());
        var graph=agent.perguntarComFonte("Agora transforme a tabela em gráfico.",first.conversationId(),"alice");
        assertEquals("horizontalBar",spec(graph).get("chartType"));assertEquals(spec(table).get("data"),spec(graph).get("data"));
        steps(call("planejar_visualizacao",Map.of("type","table")),answer());
        var back=agent.perguntarComFonte("Transforme o gráfico em tabela.",first.conversationId(),"alice");
        assertEquals(spec(table).get("rows"),spec(back).get("rows"));
    }
    @Test void stateFilterPersistsDuringConversionAndProviderOutage() {
        steps(call("consultar_dataterra",Map.of("operacao","MUNICIPIOS","estado","SP")),answer());
        var first=agent.perguntarComFonte("Liste todos os municípios de SP em tabela.",null,"alice");
        when(client.generate(anyList(),anyString())).thenThrow(new ResourceAccessException("secret"));
        var second=agent.perguntarComFonte("Agora transforme em gráfico.",first.conversationId(),"alice");
        assertEquals(49,((List<?>)spec(second).get("data")).size());assertFalse(second.response().contains("secret"));
        assertTrue(((Map<?,?>)spec(second).get("metadata")).get("queries").toString().contains("SP"));
    }
    @Test void invalidGeminiSpecificationCanBeCorrectedAndCannotSupplyValues() {
        steps(call("consultar_dataterra",Map.of("operacao","ESTADOS")),call("planejar_visualizacao",Map.of("type","chart","y","inventado")),call("planejar_visualizacao",Map.of("type","chart","x","estado","y","quantidade")),answer());
        var result=agent.perguntarComFonte("Gráfico de data centers por UF.",null,"alice");
        assertEquals("bar",spec(result).get("chartType"));assertEquals(2,((List<?>)spec(result).get("data")).size());
        verify(client,atLeastOnce()).generate(argThat(h->h.toString().contains("Campo inexistente")),anyString());
    }
    @Test void transportFailureAfterQueryPreservesRealVisualization() {
        doReturn(call("consultar_dataterra",Map.of("operacao","ESTADOS"))).doThrow(new ResourceAccessException("secret")).when(client).generate(anyList(),anyString());
        var result=agent.perguntarComFonte("Gráfico por UF.",null,"alice");
        assertEquals("bar",spec(result).get("chartType"));assertEquals(2,((List<?>)spec(result).get("data")).size());
    }
    @Test void responseAndDatasetEndpointAreCompatibleAndOwnerScoped() throws Exception {
        steps(call("consultar_dataterra",Map.of("operacao","MUNICIPIOS")),answer());
        var mvc=MockMvcBuilders.standaloneSetup(new AgentController(agent),new AgentDatasetController(memory,datasets,pagination)).build();
        var result=mvc.perform(post("/api/agent/chat").principal(()->"alice").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Liste os 98 municípios em tabela.\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.chartData.schemaVersion").value("1.0")).andExpect(jsonPath("$.visualization.rows.length()").value(98))
            .andExpect(jsonPath("$.consulta").isEmpty()).andExpect(jsonPath("$.fonte").value("banco_de_dados")).andReturn();
        var body=JsonMapper.builder().build().readTree(result.getResponse().getContentAsString());
        String id=body.get("conversationId").asString(),dataset=body.get("visualization").get("datasetId").asString();
        String url="/api/agent/conversations/"+id+"/datasets/"+dataset;
        mvc.perform(get(url).principal(()->"alice").param("page","4").param("size","20")).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(18)).andExpect(jsonPath("$.hasNext").value(false));
        mvc.perform(get(url).principal(()->"bob")).andExpect(status().isNotFound());
        mvc.perform(get(url).principal(()->"alice").param("size","1000")).andExpect(status().isBadRequest());
        // Real endpoint output from this isolated H2 fixture, suitable for frontend examples.
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/visualization-endpoint-example.json"),result.getResponse().getContentAsString());
    }
    @Test void referencesReexecuteSameParametersWhenMemoryBudgetOmitsRows() {
        var bounded=new AgentConversationService(store,8,8000,7);
        var session=bounded.open(null,"alice");
        var query=new LinkedHashMap<>(pagination.execute(Map.of("operacao","MUNICIPIOS","estado","SP"),true));
        var result=new LinkedHashMap<>((Map<String,Object>)query.get("resultado"));
        var rows=new ArrayList<Map<String,Object>>();for(int i=0;i<49;i++) rows.add(Map.of("municipio","x".repeat(1000),"estado","SP","quantidade",1));
        result.put("registros",rows);query.put("resultado",result);
        bounded.save(session,"Liste todos de SP","Dados consultados","banco_de_dados",List.of(query));
        assertTrue(Boolean.TRUE.equals(bounded.results(bounded.open(session.id(),"alice")).getFirst().get("_requiresRefresh")));
        var restarted=new AgentService(client,database,new AgentFallbackFormatter(),bounded,mock(AgentDebugSettings.class),new AgentToolTelemetry(),new AgentComparisonService(database),visualizations,datasets,pagination);
        when(client.generate(anyList(),anyString())).thenThrow(new ResourceAccessException("offline"));
        var response=restarted.perguntarComFonte("Agora em gráfico.",session.id(),"alice");
        assertEquals(49,((List<?>)spec(response).get("data")).size());assertTrue(response.response().contains("consulta atual"));
    }
    @Test void schemaOnlyPlanningNeverSendsTheNinetyEightRowsToGemini() {
        steps(call("consultar_dataterra",Map.of("operacao","MUNICIPIOS")),call("planejar_visualizacao",Map.of("type","chart")),answer());
        agent.perguntarComFonte("Gráfico de todos os municípios.",null,"alice");
        verify(client,times(3)).generate(argThat(h->!h.toString().contains("Cidade 097")),anyString());
    }
    @Test void defaultModelPageSizeDoesNotTruncateTwentySevenUfs() {
        jdbc.update("DELETE FROM data_center");
        for(String state:List.of("AC","AL","AP","AM","BA","CE","DF","ES","GO","MA","MT","MS","MG","PA","PB","PR","PE","PI","RJ","RN","RS","RO","RR","SC","SP","SE","TO"))
            jdbc.update("INSERT INTO data_center(nome,cidade,estado) VALUES ('DC','Capital',?)",state);
        steps(call("consultar_dataterra",Map.of("operacao","ESTADOS","limite",20)),answer());
        var response=agent.perguntarComFonte("Faça um gráfico de data centers por UF.",null,"alice");
        assertEquals(27,((List<?>)spec(response).get("data")).size());assertEquals(true,((Map<?,?>)spec(response).get("metadata")).get("complete"));
    }
    @Test void explicitTopFiveIsRespected() {
        steps(call("consultar_dataterra",Map.of("operacao","MUNICIPIOS","ranking",true,"limite",5)),answer());
        var response=agent.perguntarComFonte("Compare os cinco municípios com mais instalações em gráfico.",null,"alice");
        assertEquals(5,((List<?>)spec(response).get("data")).size());
    }
    private void seedTwentyFiveUfs() {
        jdbc.update("DELETE FROM data_center");
        for(String state:List.of("AC","AL","AP","AM","BA","CE","DF","ES","GO","MA","MT","MS","MG","PA","PB","PR","PE","PI","RJ","RN","RS","RO","RR","SC","SP"))
            jdbc.update("INSERT INTO data_center(nome,cidade,estado) VALUES ('DC','Capital',?)",state);
    }
    @Test void fullConversationChangesOnlyPresentationAndNeverRequeriesTwentyFiveStates() {
        seedTwentyFiveUfs();
        var tracked=spy(pagination);
        agent=new AgentService(client,database,new AgentFallbackFormatter(),memory,mock(AgentDebugSettings.class),new AgentToolTelemetry(),new AgentComparisonService(database),visualizations,datasets,tracked);
        steps(call("consultar_dataterra",Map.of("operacao","ESTADOS")),call("planejar_visualizacao",Map.of("type","chart","chartType","horizontalBar")),answer());
        var first=agent.perguntarComFonte("Mostre a quantidade de data centers por UF em gráfico de barras.",null,"alice");
        assertEquals("bar",spec(first).get("chartType"));
        String data=JsonMapper.builder().build().writeValueAsString(spec(first).get("data"));
        Object dataset=spec(first).get("datasetId");
        clearInvocations(tracked);
        String[] questions={"Agora em gráfico de pizza.","Transforme em gráfico de rosca.","Volte para barras horizontais.","Agora em formato de tabela."};
        String[] expected={"pie","donut","horizontalBar","table"};
        for(int i=0;i<questions.length;i++) {
            // Conflicting Gemini defaults and unnecessary query attempts cannot override the request.
            steps(call("consultar_dataterra",Map.of("operacao","ESTADOS","limite",10)),call("planejar_visualizacao",Map.of("type","chart","chartType","bar")),answer());
            var next=agent.perguntarComFonte(questions[i],first.conversationId(),"alice");
            assertEquals(expected[i],spec(next).get(i==3?"type":"chartType"));
            assertEquals(dataset,spec(next).get("datasetId"));assertEquals(first.conversationId(),next.conversationId());
            assertEquals(data,JsonMapper.builder().build().writeValueAsString(spec(next).get("data")));
            assertEquals(next.chartData(),next.visualization());assertEquals(25,((List<?>)spec(next).get("data")).size());
        }
        verifyNoInteractions(tracked);
    }
    @Test void exactShortPieRequestUsesPriorDatasetAndSerializesBothAliases() throws Exception {
        seedTwentyFiveUfs();
        steps(call("consultar_dataterra",Map.of("operacao","ESTADOS")),answer());
        var first=agent.perguntarComFonte("Gráfico de barras por UF.",null,"alice");
        steps(call("planejar_visualizacao",Map.of("type","chart","chartType","horizontalBar")),answer());
        var mvc=MockMvcBuilders.standaloneSetup(new AgentController(agent)).build();
        var result=mvc.perform(post("/api/agent/chat").principal(()->"alice").contentType(MediaType.APPLICATION_JSON)
            .content("{\"message\":\"Em gráfico de pie.\",\"conversationId\":\""+first.conversationId()+"\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.visualization.chartType").value("pie"))
            .andExpect(jsonPath("$.chartData.chartType").value("pie")).andExpect(jsonPath("$.visualization.data.length()").value(25))
            .andExpect(jsonPath("$.visualization.datasetId").value(spec(first).get("datasetId")))
            .andExpect(jsonPath("$.visualization.encoding.color.field").value("estado"))
            .andExpect(jsonPath("$.visualization.encoding.theta.field").value("quantidade")).andReturn();
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/chart-type-pie-regression.json"),result.getResponse().getContentAsString());
    }
    @Test void semanticGeminiConversionCanSelectAuthorizedPreviousDatasetWithoutKeywords() {
        steps(call("consultar_dataterra",Map.of("operacao","ESTADOS")),answer());
        var first=agent.perguntarComFonte("Gráfico de barras por UF.",null,"alice");
        steps(call("planejar_visualizacao",Map.of("type","chart","chartType","pie","datasetId",spec(first).get("datasetId"))),answer());
        var next=agent.perguntarComFonte("Use um círculo dividido proporcionalmente, mantendo os dados.",first.conversationId(),"alice");
        assertEquals("pie",spec(next).get("chartType"));assertEquals(spec(first).get("datasetId"),spec(next).get("datasetId"));
        assertEquals(JsonMapper.builder().build().writeValueAsString(spec(first).get("data")),JsonMapper.builder().build().writeValueAsString(spec(next).get("data")));
    }
    @Test void standalonePieRequestOverridesModelAndOutageKeepsExplicitDonut() {
        seedTwentyFiveUfs();
        steps(call("consultar_dataterra",Map.of("operacao","ESTADOS")),call("planejar_visualizacao",Map.of("type","chart","chartType","horizontalBar")),answer());
        var first=agent.perguntarComFonte("Pie chart de data centers por UF.",null,"alice");
        assertEquals("pie",spec(first).get("chartType"));assertEquals(25,((List<?>)spec(first).get("data")).size());
        when(client.generate(anyList(),anyString())).thenThrow(new ResourceAccessException("offline"));
        var next=agent.perguntarComFonte("Donut chart",first.conversationId(),"alice");
        assertEquals("donut",spec(next).get("chartType"));assertEquals(spec(first).get("datasetId"),spec(next).get("datasetId"));
    }
}
