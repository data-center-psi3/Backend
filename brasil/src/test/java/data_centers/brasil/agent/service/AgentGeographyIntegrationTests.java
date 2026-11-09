package data_centers.brasil.agent.service;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import data_centers.brasil.filter.TextNormalization;
import data_centers.brasil.dto.DataCenterResponse;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"gemini.api.key=test", "spring.datasource.url=jdbc:h2:mem:agent-geo-tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"})
@ActiveProfiles("test") @Transactional
class AgentGeographyIntegrationTests {
    @Autowired AgentDataBaseService agent;
    @Autowired JdbcTemplate jdbc;
    @Autowired data_centers.brasil.agent.memory.AgentConversationService memory;
    @BeforeEach void seed() {
        jdbc.update("INSERT INTO data_center(nome,empresa,cidade,estado) VALUES ('Campus A','Operação Alpha','Hortolandia','SP'),('Campus B','Operadora B','Hortolandia','SP'),('Outro','Outra','Hortolandia Nova','SP'),('  São   José A  ','  Operação   Alpha  ','  São   Paulo  ','SP')");
    }
    private Map<?,?> result(Map<String,Object> args) { return (Map<?,?>)agent.executar(args).get("resultado"); }
    @SuppressWarnings("unchecked")
    @Test void accentedUnaccentedUppercaseAndWhitespaceMatchSameExactCity() {
        var expected=jdbc.queryForList("SELECT nome FROM data_center WHERE LOWER(cidade) = LOWER(?) ORDER BY nome",String.class,"Hortolandia");
        assertEquals(List.of("Campus A","Campus B"),expected);
        for(String city:List.of("Hortolândia","Hortolandia","HORTOLANDIA","  Hortolândia  ")) {
            var page=result(Map.of("operacao","LISTAR","cidade",city));
            var records=(List<DataCenterResponse>)page.get("registros");
            assertEquals(expected,records.stream().map(DataCenterResponse::nome).sorted().toList());
            assertEquals(2L,result(Map.of("operacao","CONTAR","cidade",city)).get("total"));
            assertEquals(2,((List<?>)agent.executar(Map.of("operacao","OPERADORES","cidade",city)).get("resultado")).size());
            assertEquals(2L,jdbc.queryForObject("SELECT COUNT(*) FROM data_center WHERE "+TextNormalization.sql("cidade")+" = ?",Long.class,TextNormalization.normalize(city)));
        }
        assertEquals("Hortolandia",jdbc.queryForObject("SELECT cidade FROM data_center WHERE nome='Campus A'",String.class));
        assertEquals(0L,result(Map.of("operacao","CONTAR","cidade","Hortol" )).get("total"));
    }
    @Test void normalizesBothStoredAndProvidedSpacesAndStateNames() {
        for(String city:List.of("São Paulo","Sao Paulo"," SAO   PAULO "))
            assertEquals(1L,result(Map.of("operacao","CONTAR","cidade",city,"estado"," São   Paulo ")).get("total"));
        assertEquals(4L,result(Map.of("operacao","CONTAR","estado","sp")).get("total"));
        assertEquals(4L,result(Map.of("operacao","CONTAR","estado","Sao Paulo")).get("total"));
        assertEquals(1L,result(Map.of("operacao","CONTAR","nome","sao jose a")).get("total"));
        assertEquals(2L,result(Map.of("operacao","CONTAR","empresa","operacao alpha")).get("total"));
        assertEquals(0L,result(Map.of("operacao","CONTAR","empresa","operacao")).get("total"));
        assertEquals(0L,result(Map.of("operacao","CONTAR","cidade","' OR 1=1 --")).get("total"));
    }    @SuppressWarnings("unchecked")
    @Test void fiveQuestionVariantsUseMatchingDatabaseResultsThroughTheAgent() {
        String[] questions={"Liste os data centers de Hortolândia.","Liste os data centers de Hortolandia.","Quais instalações existem em HORTOLANDIA?","Quantos data centers estão cadastrados em Hortolândia?","Quais empresas possuem instalações em Hortolândia?"};
        for(int i=0;i<questions.length;i++) {
            var client=org.mockito.Mockito.mock(data_centers.brasil.agent.client.GeminiClient.class);
            String op=i<3?"LISTAR":i==3?"CONTAR":"OPERADORES";
            var tool=Map.of("role","model","parts",List.of(Map.of("functionCall",Map.of("name","consultar_dataterra","args",Map.of("operacao",op,"cidade",i==2?"HORTOLANDIA":i==1?"Hortolandia":"Hortolândia")))));
            var answer=Map.of("role","model","parts",List.of(Map.of("text","Resultado consultado.")));
            org.mockito.Mockito.doReturn(tool,answer).when(client).generate(org.mockito.ArgumentMatchers.anyList(),org.mockito.ArgumentMatchers.anyString());
            var service=new AgentService(client,agent,new data_centers.brasil.agent.formatter.AgentFallbackFormatter(),memory,
                org.mockito.Mockito.mock(data_centers.brasil.agent.config.AgentDebugSettings.class),new AgentToolTelemetry(),new AgentComparisonService(agent));
            var response=service.perguntarComFonte(questions[i],null,"geo-user");
            var evidence=memory.results(memory.open(response.conversationId(),"geo-user")).getFirst().get("resultado");
            if(i==3) assertEquals(2,((Number)((Map<?,?>)evidence).get("total")).intValue());
            else if(i==4) assertEquals(2,((List<?>)evidence).size());
            else assertEquals(2,((List<?>)((Map<?,?>)evidence).get("registros")).size());
        }
    }
    @Test void decomposedAccentsAndNonbreakingSpacesAreNormalizedOnTheStoredSide() {
        jdbc.update("INSERT INTO data_center(nome,empresa,cidade,estado) VALUES ('Unicode','Operador','Hortola\u0302ndia','SP')");
        assertEquals(3L,result(Map.of("operacao","CONTAR","cidade","Hortolândia")).get("total"));
        jdbc.update("INSERT INTO data_center(nome,cidade,estado) VALUES ('Espacos','São\u00a0\u00a0Paulo','SP')");
        assertEquals(2L,result(Map.of("operacao","CONTAR","cidade","Sao Paulo")).get("total"));
        assertEquals(3L,((Map<?,?>)agent.executar(Map.of("operacao","MUNICIPIOS")).get("resultado")).get("totalMunicipios"));
    }

}
