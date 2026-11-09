package data_centers.brasil.agent.service;

import org.junit.jupiter.api.*;
import data_centers.brasil.agent.config.AgentDebugSettings;
import data_centers.brasil.agent.memory.AgentConversationService;
import static org.mockito.Mockito.*;
import data_centers.brasil.agent.client.GeminiClient;
import data_centers.brasil.agent.context.AgentContext;
import data_centers.brasil.agent.tool.AgentToolDeclarations;
import data_centers.brasil.agent.formatter.AgentFallbackFormatter;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AgentServiceTests {
    AgentDataBaseService database;
    MockRestServiceServer server;
    AgentService agent;
    private AgentDebugSettings debugSettings() {
        var settings=mock(AgentDebugSettings.class);when(settings.sqlAllowed()).thenReturn(true);return settings;
    }
    @BeforeEach void setup() {
        database = mock(AgentDataBaseService.class);
        var builder = RestClient.builder().baseUrl("https://gemini.test");
        server = MockRestServiceServer.bindTo(builder).build();
        agent = new AgentService(new GeminiClient(builder.build(), "test-model", new AgentContext(), new AgentToolDeclarations()), database, new AgentFallbackFormatter(), mock(AgentConversationService.class), debugSettings(), new AgentToolTelemetry(), new AgentComparisonService(database));
    }
    private static String answer(String text) {
        return "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\""+text+"\"}]}}]}";
    }
    private static String call(String name, String args) {
        return "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"thoughtSignature\":\"opaque-signature\",\"functionCall\":{\"id\":\"call-1\",\"name\":\""+name+"\",\"args\":"+args+"}}]}}]}";
    }
    @Test void toolRoundTripPreservesSignatureIdAndDatabaseResult() {
        when(database.executar(Map.of("operacao", "CONTAR"))).thenReturn(Map.of("total", 37));
        server.expect(requestTo("https://gemini.test/models/test-model:generateContent"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("ANY")))
            .andRespond(withSuccess(call("consultar_dataterra", "{\"operacao\":\"CONTAR\"}"), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://gemini.test/models/test-model:generateContent"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("opaque-signature")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("functionResponse")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("call-1")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("37")))
            .andRespond(withSuccess(answer("37 registros cadastrados."), MediaType.APPLICATION_JSON));
        var result = agent.perguntarComFonte("Quantos data centers existem?");
        assertEquals("37 registros cadastrados.", result.response());
        assertEquals("banco_de_dados", result.fonte());
        verify(database).executar(Map.of("operacao", "CONTAR"));
        server.verify();
    }
    @Test void conceptualQuestionDoesNotQueryDatabase() {
        server.expect(anything()).andRespond(withSuccess(call("resposta_conceitual", "{}"), MediaType.APPLICATION_JSON));
        server.expect(anything()).andRespond(withSuccess(answer("PUE compara energia total e energia de TI."), MediaType.APPLICATION_JSON));
        var result = agent.perguntarComFonte("O que significa PUE?");
        assertTrue(result.response().contains("PUE"));
        assertEquals("conhecimento_geral_gemini", result.fonte());
        verifyNoInteractions(database);
        server.verify();
    }
    @Test void retries503And429ThenSucceeds() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(anything()).andRespond(withSuccess(call("resposta_conceitual", "{}"), MediaType.APPLICATION_JSON));
        server.expect(anything()).andRespond(withSuccess(answer("Conceito geral."), MediaType.APPLICATION_JSON));
        assertEquals("Conceito geral.", agent.perguntar("Explique um conceito."));
        server.verify();
    }
    @Test void connectionFailureIsRetriedWithLimit() {
        for (int i=0; i<3; i++) server.expect(anything()).andRespond(request -> { throw new java.net.SocketTimeoutException(); });
        assertEquals(503, assertThrows(ResponseStatusException.class, () -> agent.perguntar("Pergunta")).getStatusCode().value());
        server.verify();
    }
    @Test void permanentHttpFailureIsNotRetriedOrExposed() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.BAD_REQUEST).body("secret-provider-detail"));
        var ex = assertThrows(ResponseStatusException.class, () -> agent.perguntar("Pergunta"));
        assertFalse(ex.getReason().contains("secret"));
        server.verify();
    }
    @Test void emptyMalformedAndUnverifiedTextAreRejected() {
        for (String body : List.of("{}", "{broken", answer("999 instalações"))) {
            setup();
            server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
            assertThrows(ResponseStatusException.class, () -> agent.perguntar("Quantos registros?"));
            server.verify();
        }
    }
    @Test void returnsRetrievedEvidenceIfGeminiFailsAfterTool() {
        when(database.executar(anyMap())).thenReturn(Map.of("operacao", "CONTAR", "resultado", Map.of("total", 17, "filtros", Map.of())));
        server.expect(anything()).andRespond(withSuccess(call("consultar_dataterra", "{\"operacao\":\"CONTAR\"}"), MediaType.APPLICATION_JSON));
        for (int i=0; i<3; i++) server.expect(anything()).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        var result = agent.perguntarComFonte("Quantos?");
        assertTrue(result.response().contains("17"));
        assertEquals("banco_de_dados", result.fonte());
        server.verify();
    }
    @Test void statePagesAreConsolidatedWhenGeminiIsUnavailable() {
        when(database.executar(Map.of("operacao","ESTADOS","pagina",0,"limite",2))).thenReturn(
            Map.of("operacao","ESTADOS","pagina",0,"limite",2,"resultado",List.of(Map.of("estado","SP","quantidade",112),Map.of("estado","RJ","quantidade",34))));
        when(database.executar(Map.of("operacao","ESTADOS","pagina",1,"limite",2))).thenReturn(
            Map.of("operacao","ESTADOS","pagina",1,"limite",2,"resultado",List.of(Map.of("estado","RS","quantidade",32))));
        server.expect(anything()).andRespond(withSuccess(call("consultar_dataterra","{\"operacao\":\"ESTADOS\",\"pagina\":0,\"limite\":2}"),MediaType.APPLICATION_JSON));
        server.expect(anything()).andRespond(withSuccess(call("consultar_dataterra","{\"operacao\":\"ESTADOS\",\"pagina\":1,\"limite\":2}"),MediaType.APPLICATION_JSON));
        for (int i=0;i<3;i++) server.expect(anything()).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        var result=agent.perguntarComFonte("Distribuicao por estado");
        assertEquals("banco_de_dados",result.fonte());
        assertTrue(result.response().contains("São Paulo: 112"));
        assertTrue(result.response().contains("Rio de Janeiro: 34"));
        assertTrue(result.response().contains("Rio Grande do Sul: 32"));
        assertFalse(result.response().contains("resultado="));
        assertFalse(result.response().contains("parciais"));
        verify(database,times(2)).executar(anyMap());
        server.verify();
    }
    @Test void toolFailureIsSanitizedAndCanBeCorrected() {
        when(database.executar(anyMap())).thenThrow(new RuntimeException("password=secret"));
        server.expect(anything()).andRespond(withSuccess(call("consultar_dataterra", "{\"operacao\":\"CONTAR\"}"), MediaType.APPLICATION_JSON));
        server.expect(anything()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("password=secret"))))
            .andRespond(withSuccess(answer("Sem dados"), MediaType.APPLICATION_JSON));
        assertThrows(ResponseStatusException.class, () -> agent.perguntar("Quantos?"));
    }
    @Test void unauthorizedToolNeverInvokesDatabase() {
        server.expect(anything()).andRespond(withSuccess(call("executar_sql", "{}"), MediaType.APPLICATION_JSON));
        server.expect(anything()).andRespond(withSuccess(answer("OK"), MediaType.APPLICATION_JSON));
        assertThrows(ResponseStatusException.class, () -> agent.perguntar("DROP TABLE data_center"));
        verifyNoInteractions(database);
    }
    @Test void parallelCallsCompareStatesAndKeepBothResults() {
        when(database.executar(Map.of("operacao","CONTAR","estado","SP"))).thenReturn(Map.of("total",23));
        when(database.executar(Map.of("operacao","CONTAR","estado","RJ"))).thenReturn(Map.of("total",7));
        String parallel = "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":["
            + "{\"functionCall\":{\"name\":\"consultar_dataterra\",\"args\":{\"operacao\":\"CONTAR\",\"estado\":\"SP\"}}},"
            + "{\"functionCall\":{\"name\":\"consultar_dataterra\",\"args\":{\"operacao\":\"CONTAR\",\"estado\":\"RJ\"}}}]}}]}";
        server.expect(anything()).andRespond(withSuccess(parallel,MediaType.APPLICATION_JSON));
        server.expect(anything()).andExpect(content().string(org.hamcrest.Matchers.containsString("23")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("7")))
            .andRespond(withSuccess(answer("SP: 23; RJ: 7 cadastrados."),MediaType.APPLICATION_JSON));
        assertEquals("SP: 23; RJ: 7 cadastrados.",agent.perguntar("Compare SP e RJ"));
        verify(database,times(2)).executar(anyMap());
        server.verify();
    }
    @Test void argumentValidationCanBeCorrectedByModel() {
        when(database.executar(Map.of("operacao","CONTAR","estado","XX"))).thenThrow(new IllegalArgumentException("UF invalida"));
        when(database.executar(Map.of("operacao","CONTAR","estado","SP"))).thenReturn(Map.of("total",11));
        server.expect(anything()).andRespond(withSuccess(call("consultar_dataterra","{\"operacao\":\"CONTAR\",\"estado\":\"XX\"}"),MediaType.APPLICATION_JSON));
        server.expect(anything()).andExpect(content().string(org.hamcrest.Matchers.containsString("UF invalida")))
            .andRespond(withSuccess(call("consultar_dataterra","{\"operacao\":\"CONTAR\",\"estado\":\"SP\"}"),MediaType.APPLICATION_JSON));
        server.expect(anything()).andRespond(withSuccess(answer("11 cadastrados em SP."),MediaType.APPLICATION_JSON));
        var result=agent.perguntarComFonte("Quantos em SP?");
        assertEquals("11 cadastrados em SP.",result.response());
        server.verify();
    }
    @Test void noArgumentConceptualCallMayOmitArgs() {
        String response=call("resposta_conceitual","{}").replace(",\"args\":{}", "");
        server.expect(anything()).andRespond(withSuccess(response,MediaType.APPLICATION_JSON));
        server.expect(anything()).andRespond(withSuccess(answer("Conceito."),MediaType.APPLICATION_JSON));
        assertEquals("Conceito.",agent.perguntar("Explique PUE"));
        server.verify();
    }
    @Test void mixedQuestionReportsBothSources() {
        when(database.executar(anyMap())).thenReturn(Map.of("total",5));
        server.expect(anything()).andRespond(withSuccess(call("consultar_dataterra","{\"operacao\":\"CONTAR\"}"),MediaType.APPLICATION_JSON));
        server.expect(anything()).andRespond(withSuccess(call("resposta_conceitual","{}"),MediaType.APPLICATION_JSON));
        server.expect(anything()).andRespond(withSuccess(answer("5 registros. PUE e um indicador."),MediaType.APPLICATION_JSON));
        var result=agent.perguntarComFonte("Quantos registros existem e o que significa PUE?");
        assertEquals("banco_de_dados_e_conhecimento_geral_gemini",result.fonte());
        server.verify();
    }
    @Test void invalidUserMessagesAre400() {
        for (String question : Arrays.asList(null, " ", "a".repeat(4001)))
            assertEquals(400, assertThrows(ResponseStatusException.class, () -> agent.perguntar(question)).getStatusCode().value());
        verifyNoInteractions(database);
    }    @Test void identicalToolCallsWithinOneQuestionDoNotRepeatDatabaseQueries() {
        when(database.executar(Map.of("operacao","CONTAR"))).thenReturn(Map.of("operacao","CONTAR","resultado",Map.of("total",5)));
        server.expect(anything()).andRespond(withSuccess(call("consultar_dataterra","{\"operacao\":\"CONTAR\"}"),MediaType.APPLICATION_JSON));
        server.expect(anything()).andRespond(withSuccess(call("consultar_dataterra","{\"operacao\":\"CONTAR\"}"),MediaType.APPLICATION_JSON));
        server.expect(anything()).andRespond(withSuccess(answer("5 cadastrados."),MediaType.APPLICATION_JSON));
        assertEquals("5 cadastrados.",agent.perguntar("Quantos?"));
        verify(database,times(1)).executar(Map.of("operacao","CONTAR"));server.verify();
    }

}
