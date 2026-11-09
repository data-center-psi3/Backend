package data_centers.brasil.agent.service;

import data_centers.brasil.agent.dto.MunicipioCount;
import data_centers.brasil.agent.client.GeminiClient;
import data_centers.brasil.agent.formatter.AgentFallbackFormatter;
import data_centers.brasil.agent.config.AgentDebugSettings;
import data_centers.brasil.agent.memory.AgentConversationService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"gemini.api.key=test","agent.municipios.max-records=100","agent.municipios.max-pages=10","spring.datasource.url=jdbc:h2:mem:agent-municipios;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"})
@ActiveProfiles("test") @Transactional
class AgentMunicipiosIntegrationTests {
    @Autowired AgentDataBaseService database;
    @Autowired JdbcTemplate jdbc;
    @Autowired AgentConversationService memory;
    @BeforeEach void clean() { jdbc.update("DELETE FROM data_center"); }
    void seed(int count) {
        for(int i=0;i<count;i++) jdbc.update("INSERT INTO data_center(nome,cidade,estado) VALUES (?,?,?)","DC "+i,String.format("Cidade %03d",i),i%2==0?"SP":"RJ");
    }
    Map<?,?> query(Map<String,Object> args) { return (Map<?,?>)database.executar(args).get("resultado"); }
    @SuppressWarnings("unchecked") List<MunicipioCount> rows(Map<?,?> result) { return (List<MunicipioCount>)result.get("registros"); }
    @ParameterizedTest @ValueSource(ints={0,20,21,98})
    void retrievesEveryPageAndPreservesTotals(int count) {
        seed(count);
        var result=query(Map.of("operacao","MUNICIPIOS","todos",true));
        assertEquals(count,rows(result).size()); assertEquals((long)count,result.get("totalElements"));
        assertEquals((long)count,result.get("totalInstalacoesRetornadas"));
        assertEquals(true,result.get("completo")); assertEquals(false,result.get("hasNext"));
        assertEquals(Math.max(1,(count+19)/20),result.get("paginasConsultadas"));
        var keys=rows(result).stream().map(r->r.estado()+r.municipio()).toList();
        assertEquals(keys.stream().sorted().toList(),keys);
    }
    @Test void explicitPagesAndStateFilter() {
        seed(98);
        var first=query(Map.of("operacao","MUNICIPIOS"));
        assertEquals(20,rows(first).size()); assertEquals(true,first.get("hasNext"));
        var second=query(Map.of("operacao","MUNICIPIOS","pagina",1));
        assertTrue(Collections.disjoint(rows(first),rows(second)));
        var sp=query(Map.of("operacao","MUNICIPIOS","estado","São Paulo","todos",true));
        assertEquals(49,rows(sp).size()); assertTrue(rows(sp).stream().allMatch(r->r.estado().equals("SP")));
        assertEquals(49L,sp.get("totalInstalacoesRetornadas"));
    }
    @Test void normalizationKeepsStatesAndDistinctNamesSeparateAndExcludesBlankCities() {
        for(String city:List.of("São Paulo"," SAO   PAULO ","Sa\u0303o Paulo"))
            jdbc.update("INSERT INTO data_center(nome,cidade,estado) VALUES ('DC',?,'SP')",city);
        jdbc.update("INSERT INTO data_center(nome,cidade,estado) VALUES ('Other','São Paulo','RJ'),('Different','São Paulo Novo','SP'),('Blank','  ','SP')");
        var result=query(Map.of("operacao","MUNICIPIOS","todos",true));
        assertEquals(3,rows(result).size()); assertEquals(5L,result.get("totalInstalacoesRetornadas"));
        assertEquals(3L,rows(query(Map.of("operacao","MUNICIPIOS","cidade","sao paulo","estado","sp"))).getFirst().quantidade());
    }
    @Test void topTenUsesRealCounts() {
        seed(21);
        jdbc.update("INSERT INTO data_center(nome,cidade,estado) VALUES ('Extra','Cidade 020','SP')");
        var result=query(Map.of("operacao","MUNICIPIOS","ranking",true,"limite",10));
        assertEquals(10,rows(result).size()); assertEquals("Cidade 020",rows(result).getFirst().municipio());
        assertEquals(2,rows(result).getFirst().quantidade());
    }
    @Test void boundedCompleteRequestReportsExplicitContinuation() {
        seed(121);
        var result=query(Map.of("operacao","MUNICIPIOS","todos",true));
        assertEquals(100,rows(result).size()); assertEquals(false,result.get("completo"));
        assertEquals(true,result.get("hasNext")); assertTrue(result.get("alternativa").toString().contains("pagina=5"));
    }
    @Test void allMunicipalitiesReachGeminiAndDeterministicTextAndChartDespiteSummary() {
        seed(98);
        var client=mock(GeminiClient.class);
        doReturn(Map.of("role","model","parts",List.of(Map.of("functionCall",Map.of("name","consultar_dataterra","args",Map.of("operacao","MUNICIPIOS"))))),
            Map.of("role","model","parts",List.of(Map.of("text","Resumo incompleto."))))
            .when(client).generate(anyList(),anyString());
        var service=new AgentService(client,database,new AgentFallbackFormatter(),memory,mock(AgentDebugSettings.class),new AgentToolTelemetry(),new AgentComparisonService(database));
        var response=service.perguntarComFonte("Liste todos os municípios e faça um gráfico.",null,"municipios-user");
        assertTrue(response.response().contains("98 municípios retornados"));
        var chart=(Map<?,?>)response.chartData(); assertEquals(98,((List<?>)chart.get("data")).size());
        assertTrue(chart.get("data").toString().contains("Cidade 097"));
        verify(client,times(2)).generate(argThat(h->h.toString().contains("datasetId") && !h.toString().contains("Cidade 097")),anyString());
        assertEquals(98,((List<?>)((Map<?,?>)memory.results(memory.open(response.conversationId(),"municipios-user")).getFirst().get("resultado")).get("registros")).size());
    }
}
