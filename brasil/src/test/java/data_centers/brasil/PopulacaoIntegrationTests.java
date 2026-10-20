package data_centers.brasil;

import data_centers.brasil.dto.CargaResponse;
import data_centers.brasil.exception.*;
import data_centers.brasil.importacao.CsvDataCenterReader;
import data_centers.brasil.repository.*;
import data_centers.brasil.service.PopulacaoService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PopulacaoIntegrationTests {
    @Autowired PopulacaoService service;
    @Autowired DataCenterRepository dataCenters;
    @Autowired FonteRepository fontes;
    @Autowired JdbcTemplate jdbc;
    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void setup() { mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }

    @Test
    void previewsActualCsvWithoutWriting() throws Exception {
        mvc.perform(get("/api/importacoes/previa").with(httpBasic("test", "test-password")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalLinhas").value(352))
            .andExpect(jsonPath("$.registrosComCoordenadas").value(348))
            .andExpect(jsonPath("$.amostra.length()").value(5));
        assertEquals(0, dataCenters.count());
        assertEquals(0, fontes.count());
        assertEquals(0, count("data_center_importacao"));
    }

    @Test
    void loadsAllActualRowsAndRepeatedLoadCreatesNothing() {
        CargaResponse first = service.populateClasspath();
        assertEquals(352, first.totalLinhas());
        assertEquals(352, first.dataCentersCriados());
        assertEquals(352, first.fontesCriadas());
        assertEquals(352, first.conteudosCriados());
        assertEquals(352, first.linhasBrutasCriadas());
        assertEquals(0, first.registrosIgnorados());
        assertEquals(352, count("data_center_fonte"));
        assertFalse(first.avisos().isEmpty());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM data_center WHERE data_validacao IS NOT NULL", Integer.class));

        CargaResponse second = service.populateClasspath();
        assertEquals(352, second.registrosIgnorados());
        assertEquals(0, second.dataCentersCriados());
        assertEquals(0, second.fontesCriadas());
        assertEquals(0, second.indicadoresCriados());
        assertEquals(0, second.conteudosCriados());
        assertEquals(0, second.linhasBrutasCriadas());
        assertEquals(352, dataCenters.count());
        assertEquals(352, count("conteudo_pesquisa"));
    }

    @Test
    void parsesCommaDecimalsAndPreservesQualitativeStatements() {
        var row = row("TEST-1", "Unidade Teste");
        row.put("potencia_ti_mw_unidade", "4,8");
        row.put("potencia_campus_mw", "Mais de 20");
        row.put("pue_declarado", "Meta inferior a 1,5");
        row.put("wue_declarado", "Zero (declaração do operador)");
        var result = service.populate(csv(row));
        assertEquals(1, result.dataCentersCriados());
        assertEquals(2, result.indicadoresCriados());
        var dc = dataCenters.findAll().getFirst();
        assertEquals(0, dc.getLatitude().compareTo(new java.math.BigDecimal("-22.9056")));
        assertEquals(0, dc.getPotenciaMw().compareTo(new java.math.BigDecimal("4.8")));
        assertEquals("PENDENTE", dc.getStatusValidacao());
        assertNull(dc.getDataValidacao());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM indicador_ambiental WHERE valor IS NULL", Integer.class));
        assertTrue(jdbc.queryForObject("SELECT conteudo FROM conteudo_pesquisa", String.class).contains("Mais de 20"));
        assertEquals(0, count("beneficio"));
    }

    @Test
    void reusesExistingCadastroAndRawRowWithoutOverwritingEdits() {
        jdbc.update("INSERT INTO data_center(nome,empresa,cidade,estado,descricao) VALUES ('Unidade Teste','Operador Teste','Campinas','SP','Edição manual')");
        jdbc.update("INSERT INTO data_center_importacao(id_original) VALUES ('TEST-1')");
        var result = service.populate(csv(row("TEST-1", "Unidade Teste")));
        assertEquals(0, result.dataCentersCriados());
        assertEquals(1, result.dataCentersReutilizados());
        assertEquals(0, result.linhasBrutasCriadas());
        assertEquals("Edição manual", dataCenters.findAll().getFirst().getDescricao());
        assertEquals(1, count("data_center_fonte"));
    }

    @Test
    void rejectsDuplicateIdsAndInvalidCoordinatesBeforeWriting() {
        var first = row("TEST-1", "Primeiro");
        var invalid = row("TEST-2", "Segundo");
        invalid.put("latitude", "91");
        assertThrows(CsvImportException.class, () -> service.populate(csv(first, invalid)));
        assertEquals(0, dataCenters.count());
        assertThrows(CsvImportException.class, () -> service.populate(csv(first, first)));
        assertEquals(0, count("data_center_importacao"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rollsBackWholeLoadWhenLaterRecordHasAmbiguousCadastro() {
        jdbc.update("INSERT INTO data_center(nome,empresa,cidade,estado) VALUES ('Ambíguo','Operador Teste','Campinas','SP'),('Ambíguo','Operador Teste','Campinas','SP')");
        try {
            assertThrows(ResourceConflictException.class, () -> service.populate(csv(
                row("TEST-FIRST", "Primeiro"), row("TEST-AMBIGUOUS", "Ambíguo"))));
            assertEquals(2, dataCenters.count());
            assertEquals(0, fontes.count());
            assertEquals(0, count("data_center_importacao"));
            assertEquals(0, count("conteudo_pesquisa"));
        } finally {
            jdbc.update("DELETE FROM data_center WHERE nome = 'Ambíguo'");
        }
    }

    @Test
    void populationEndpointRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/importacoes/popular")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/importacoes/popular").with(httpBasic("test", "test-password")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.dataCentersCriados").value(352));
        mvc.perform(post("/api/importacoes/popular").with(httpBasic("test", "test-password")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.registrosIgnorados").value(352));
    }

    private long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class); }

    private Map<String, String> row(String id, String unidade) {
        var values = new LinkedHashMap<String, String>();
        CsvDataCenterReader.HEADERS.forEach(h -> values.put(h, "—"));
        values.put("id", id);
        values.put("operador", "Operador Teste");
        values.put("unidade", unidade);
        values.put("municipio", "Campinas");
        values.put("uf", "SP");
        values.put("latitude", "-22,9056");
        values.put("longitude", "-47,0608");
        values.put("data_levantamento", "2026-09-05");
        values.put("fonte_url", "https://example.com/registro");
        return values;
    }

    @SafeVarargs
    private final InputStream csv(Map<String, String>... rows) {
        var text = new StringBuilder(String.join(";", CsvDataCenterReader.HEADERS)).append("\n");
        for (var row : rows) {
            text.append(String.join(";", CsvDataCenterReader.HEADERS.stream().map(h ->
                "\"" + row.getOrDefault(h, "").replace("\"", "\"\"") + "\"").toList())).append("\n");
        }
        return new ByteArrayInputStream(text.toString().getBytes(StandardCharsets.UTF_8));
    }
}
