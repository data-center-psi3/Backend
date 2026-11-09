package data_centers.brasil.agent.service;

import org.junit.jupiter.api.*;
import data_centers.brasil.agent.config.AgentDebugSettings;
import data_centers.brasil.agent.memory.AgentConversationService;
import static org.mockito.Mockito.*;
import data_centers.brasil.agent.client.GeminiClient;
import data_centers.brasil.agent.context.AgentContext;
import data_centers.brasil.agent.tool.AgentToolDeclarations;
import data_centers.brasil.agent.formatter.AgentFallbackFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@SpringBootTest(properties={"gemini.api.key=test-not-a-real-key",
    "spring.datasource.url=jdbc:h2:mem:agent-sql-api-tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"})
@ActiveProfiles("test") @Transactional
class AgentRawSqlIntegrationTests {
    @Autowired AgentDataBaseService database;
    AgentService agent;
    MockRestServiceServer server;
    private static final String TOOL="{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"functionCall\":{\"name\":\"consultar_dataterra\",\"args\":{\"operacao\":\"ESTADOS\"}}}]}}]}";
    private static final String SQL="SELECT UPPER(TRIM(estado)) AS estado, COUNT(*) AS quantidade FROM data_center GROUP BY UPPER(TRIM(estado)) ORDER BY quantidade DESC, estado LIMIT ? OFFSET ?";
    private AgentDebugSettings debugSettings() {
        var settings=mock(AgentDebugSettings.class);when(settings.sqlAllowed()).thenReturn(true);return settings;
    }
    @BeforeEach void setup() {
        var builder=RestClient.builder().baseUrl("https://gemini.test");
        server=MockRestServiceServer.bindTo(builder).build();
        agent=new AgentService(new GeminiClient(builder.build(), "test-model", new AgentContext(), new AgentToolDeclarations()), database, new AgentFallbackFormatter(), mock(AgentConversationService.class), debugSettings(), new AgentToolTelemetry(), new AgentComparisonService(database));
    }
    @Test void returnsRawExecutedQueryAlongsideModelAnswer() {
        server.expect(anything()).andRespond(withSuccess(TOOL,MediaType.APPLICATION_JSON));
        server.expect(anything()).andRespond(withSuccess("{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"Nenhum registro cadastrado.\"}]}}]}",MediaType.APPLICATION_JSON));
        var response=agent.perguntarComFonte("Distribuicao por estados");
        assertEquals(java.util.List.of(SQL),response.consulta());
        assertEquals("banco_de_dados",response.fonte());
        server.verify();
    }
    @Test void preservesRawExecutedQueryDuringGeminiOutage() {
        server.expect(anything()).andRespond(withSuccess(TOOL,MediaType.APPLICATION_JSON));
        for(int i=0;i<3;i++) server.expect(anything()).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        var response=agent.perguntarComFonte("Distribuicao por estados");
        assertEquals(java.util.List.of(SQL),response.consulta());
        assertEquals("banco_de_dados",response.fonte());
        assertFalse(response.response().contains("SELECT"));
        server.verify();
    }
}
