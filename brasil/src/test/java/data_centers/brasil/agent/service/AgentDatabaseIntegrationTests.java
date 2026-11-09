package data_centers.brasil.agent.service;

import data_centers.brasil.agent.service.AgentDataBaseService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"gemini.api.key=test-not-a-real-key",
    "spring.datasource.url=jdbc:h2:mem:agent-tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"})
@ActiveProfiles("test") @Transactional
class AgentDatabaseIntegrationTests {
    @Autowired AgentDataBaseService agent;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.web.context.WebApplicationContext context;
    int dc;
    @BeforeEach void seed() {
        jdbc.update("INSERT INTO data_center(nome,empresa,cidade,estado) VALUES ('Agent Alpha','Operador A','Campinas','SP'),('Agent Beta','Operador A','Santos','SP'),('Agent Gamma','Operador B','Rio de Janeiro','RJ')");
        dc = jdbc.queryForObject("SELECT id FROM data_center WHERE nome='Agent Alpha'", Integer.class);
        jdbc.update("INSERT INTO fonte(instituicao,titulo,url,data_consulta) VALUES ('Instituto','Relatorio','https://example.test/source',CURRENT_DATE)");
        int source = jdbc.queryForObject("SELECT id FROM fonte WHERE titulo='Relatorio'", Integer.class);
        jdbc.update("INSERT INTO indicador_ambiental(data_center_id,tipo,valor,unidade,fonte_id) VALUES (?,'PUE',1.25,'indice',?)", dc,source);
        jdbc.update("INSERT INTO data_center_fonte(data_center_id,fonte_id) VALUES (?,?)",dc,source);
        jdbc.update("INSERT INTO noticia(titulo,descricao,url,fonte,data_publicacao) VALUES ('Sustentabilidade Operador A','energia','https://example.test/news','Fonte',CURRENT_TIMESTAMP)");
    }
    private Map<?,?> result(Map<String,Object> args) { return (Map<?,?>) agent.executar(args).get("resultado"); }
    private long count(Map<String,Object> args) { return ((Number)result(args).get("total")).longValue(); }
    @Test void endpointPreservesAuthenticationAndRejectsMissingMessage() throws Exception {
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
            .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/agent/chat")
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/agent/chat")
            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic("test","test-password"))
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
    }
    @Test void capturesExactJdbcAndHibernateSqlWithoutReconstruction() {
        try (var capture = data_centers.brasil.agent.sql.AgentSqlCapture.iniciar()) {
            agent.executar(Map.of("operacao","ESTADOS","limite",5));
            assertEquals(List.of("SELECT UPPER(TRIM(estado)) AS estado, COUNT(*) AS quantidade FROM data_center GROUP BY UPPER(TRIM(estado)) ORDER BY quantidade DESC, estado LIMIT ? OFFSET ?"),capture.consultas());
        }
        try (var capture = data_centers.brasil.agent.sql.AgentSqlCapture.iniciar()) {
            agent.executar(Map.of("operacao","CONTAR","estado","SP"));
            assertEquals(1,capture.consultas().size());
            String sql=capture.consultas().getFirst();
            assertTrue(sql.startsWith("select count("),sql);
            assertTrue(sql.contains("data_center"),sql);
            assertTrue(sql.contains("lower("),sql);
            assertTrue(sql.contains("=?"),sql);
        }
        try (var capture = data_centers.brasil.agent.sql.AgentSqlCapture.iniciar()) {
            assertTrue(capture.consultas().isEmpty());
        }
    }
    @Test void sqlCaptureDoesNotIncludeOtherThreadsOrFollowingRequests() {
        try (var capture = data_centers.brasil.agent.sql.AgentSqlCapture.iniciar()) {
            java.util.concurrent.CompletableFuture.runAsync(() -> jdbc.queryForObject("SELECT 1",Integer.class)).join();
            assertTrue(capture.consultas().isEmpty());
            jdbc.queryForObject("SELECT 2",Integer.class);
            assertEquals(List.of("SELECT 2"),capture.consultas());
        }
        jdbc.queryForObject("SELECT 3",Integer.class);
        try (var capture = data_centers.brasil.agent.sql.AgentSqlCapture.iniciar()) {
            assertTrue(capture.consultas().isEmpty());
        }
    }
    @Test void totalUsesWholeDatabaseAndCountFiltersStateCityOperator() {
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM data_center",Long.class);
        assertEquals(total,agent.contarDataCenters());
        assertEquals(2,count(Map.of("operacao","CONTAR","estado","sp")));
        assertEquals(1,count(Map.of("operacao","CONTAR","cidade","Campinas","estado","SP")));
        assertEquals(2,count(Map.of("operacao","CONTAR","empresa","Operador A")));
    }
    @Test void countsAreNotLimitedToFirstPage() {
        for (int i=0; i<25; i++) jdbc.update("INSERT INTO data_center(nome,cidade,estado) VALUES (?, 'Extra', 'SP')", "Extra " + i);
        assertEquals(28,agent.contarDataCenters());
        assertEquals(27,count(Map.of("operacao","CONTAR","estado","SP")));
    }
    @Test void municipalityNamesInDifferentStatesAreDistinct() {
        jdbc.update("INSERT INTO data_center(nome,cidade,estado) VALUES ('Outra', 'Campinas', 'RJ')");
        assertEquals(4,((Number)result(Map.of("operacao","MUNICIPIOS")).get("totalMunicipios")).intValue());
    }
    @Test void rankingAndDistinctMunicipalitiesUseAggregations() {
        var ranking=(List<?>)agent.executar(Map.of("operacao","ESTADOS","limite",1)).get("resultado");
        var first=(Map<?,?>)ranking.getFirst();
        assertEquals("SP",first.get("estado"));
        assertEquals(2,((Number)first.get("quantidade")).intValue());
        assertEquals(3,((Number)result(Map.of("operacao","MUNICIPIOS")).get("totalMunicipios")).intValue());
    }
    @Test void listPaginationAndMissingDataAreExplicit() {
        var page=result(Map.of("operacao","LISTAR","estado","SP","limite",1));
        assertEquals(2L,page.get("total")); assertEquals(true,page.get("temProximaPagina"));
        assertEquals(0,count(Map.of("operacao","CONTAR","cidade","Nao existe")));
        assertEquals(0L,result(Map.of("operacao","INDICADORES","termo","WUE")).get("total"));
    }
    @Test void indicatorsKeepSourceAndLinkedSourcesAreAvailable() {
        var data=result(Map.of("operacao","INDICADORES","dataCenterId",dc,"termo","PUE"));
        assertEquals(1L,data.get("total"));
        assertEquals(1L,result(Map.of("operacao","FONTES","dataCenterId",dc)).get("total"));
        assertEquals(0L,result(Map.of("operacao","INDICADORES","dataCenterId",dc,"termo","agua")).get("total"));
    }
    @Test void newsAndOperatorsUseExistingRecords() {
        assertEquals(1,((List<?>)agent.executar(Map.of("operacao","NOTICIAS","termo","Operador A")).get("resultado")).size());
        assertEquals(2,((List<?>)agent.executar(Map.of("operacao","OPERADORES")).get("resultado")).size());
        assertTrue(((List<?>)agent.executar(Map.of("operacao","NOTICIAS","termo","' OR 1=1 --")).get("resultado")).isEmpty());
    }
    @Test void rejectsSqlUnknownFieldsWrongTypesAndExcessiveLimits() {
        for (Map<String,Object> args : List.of(
            Map.<String,Object>of("operacao","DROP"), Map.<String,Object>of("operacao","CONTAR","sql","SELECT 1"),
            Map.<String,Object>of("operacao","LISTAR","limite",51), Map.<String,Object>of("operacao","LISTAR","limite",1.5),
            Map.<String,Object>of("operacao","LISTAR","pagina",-1), Map.<String,Object>of("operacao","CONTAR","estado","XX"),
            Map.<String,Object>of("operacao","CONTAR","estado",123), Map.<String,Object>of("operacao","ESTADOS","cidade","Campinas")))
            assertThrows(IllegalArgumentException.class,()->agent.executar(args));
        assertEquals(0,count(Map.of("operacao","CONTAR","nome","' OR 1=1 --")));
    }
}
