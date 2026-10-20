package data_centers.brasil;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.*;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FilterIntegrationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired WebApplicationContext context;
    MockMvc mvc;
    Map<String, Map<String, Object>> first = new HashMap<>();
    int dcA, dcB, fonteA, fonteB;

    private static final Map<String, List<String>> FIELDS = Map.of(
        "data-centers", List.of("id", "nome", "empresa", "cidade", "estado", "pais", "latitude", "longitude", "descricao", "potenciaMw", "areaM2", "tipoInstalacao", "statusValidacao", "dataValidacao"),
        "fontes", List.of("id", "instituicao", "titulo", "url", "dataPublicacao", "dataConsulta"),
        "indicadores-ambientais", List.of("id", "dataCenterId", "tipo", "valor", "unidade", "periodo", "fonteId", "observacoes"),
        "beneficios", List.of("id", "dataCenterId", "tipo", "descricao", "fonteId"),
        "conteudos-pesquisa", List.of("id", "dataCenterId", "titulo", "conteudo", "categoria", "fonteId", "dataPublicacao"),
        "importacoes", List.of("idOriginal", "operador", "unidade", "municipio", "uf", "codigoIbge", "endereco", "latitude", "longitude", "precisaoGeo", "tipoInstalacao", "statusOperacional", "inicioOperacao", "potenciaTiMwUnidade", "potenciaCampusMw", "areaM2Unidade", "redesConectadas", "ixConectados", "carriers", "voltadaIa", "resfriamento", "fonteEnergia", "pueDeclarado", "wueDeclarado", "certificacoes", "robustezEvidencia", "fonteUrl", "dataLevantamento")
    );

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var a = dataCenter("Alpha", "Campinas", "SP", "12.25", "2026-10-13");
        var b = dataCenter("Beta", "São Paulo", "RJ", "40.75", "2025-01-01");
        dcA = insert("data_center", a); dcB = insert("data_center", b);
        a.put("id", dcA);
        first.put("data-centers", a);

        var sourceA = source("Alpha", "2026-09-22");
        var sourceB = source("Beta", "2025-01-01");
        fonteA = insert("fonte", sourceA); fonteB = insert("fonte", sourceB);
        sourceA.put("id", fonteA);
        first.put("fontes", sourceA);

        var indicatorA = map("dataCenterId", dcA, "tipo", "PUE Alpha", "valor", new BigDecimal("1.25"),
            "unidade", "índice Alpha", "periodo", "2026", "fonteId", fonteA, "observacoes", "Observações Alpha");
        var indicatorB = map("dataCenterId", dcB, "tipo", "WUE Beta", "valor", new BigDecimal("2.75"),
            "unidade", "litros Beta", "periodo", "2025", "fonteId", null, "observacoes", "Observações Beta");
        int ia = insert("indicador_ambiental", indicatorA); insert("indicador_ambiental", indicatorB);
        indicatorA.put("id", ia);
        first.put("indicadores-ambientais", indicatorA);

        var benefitA = map("dataCenterId", dcA, "tipo", "Emprego Alpha", "descricao", "Benefício Alpha", "fonteId", fonteA);
        var benefitB = map("dataCenterId", dcB, "tipo", "Emprego Beta", "descricao", "Benefício Beta", "fonteId", null);
        int benefit = insert("beneficio", benefitA); insert("beneficio", benefitB);
        benefitA.put("id", benefit);
        first.put("beneficios", benefitA);

        var researchA = map("dataCenterId", dcA, "titulo", "Pesquisa Alpha", "conteudo", "Conteúdo Alpha",
            "categoria", "Infraestrutura Alpha", "fonteId", fonteA, "dataPublicacao", Date.valueOf("2026-09-22"));
        var researchB = map("dataCenterId", null, "titulo", "Pesquisa Beta", "conteudo", "Conteúdo Beta",
            "categoria", "Infraestrutura Beta", "fonteId", fonteB, "dataPublicacao", null);
        int research = insert("conteudo_pesquisa", researchA); insert("conteudo_pesquisa", researchB);
        researchA.put("id", research);
        first.put("conteudos-pesquisa", researchA);

        jdbc.update("INSERT INTO data_center_fonte(data_center_id,fonte_id) VALUES (?,?),(?,?),(?,?)",
            dcA, fonteA, dcA, fonteB, dcB, fonteB);

        var rawA = new LinkedHashMap<String, Object>();
        var rawB = new LinkedHashMap<String, Object>();
        for (String field : FIELDS.get("importacoes")) {
            rawA.put(field, field + " Alpha");
            rawB.put(field, field + " Beta");
        }
        rawA.put("idOriginal", "RAW-1"); rawB.put("idOriginal", "RAW-2");
        insert("data_center_importacao", rawA); insert("data_center_importacao", rawB);
        first.put("importacoes", rawA);
    }

    @Test
    void everyResponseFieldFiltersItsCollectionInTheDatabase() throws Exception {
        for (var resource : FIELDS.entrySet()) {
            var values = first.get(resource.getKey());
            for (String field : resource.getValue()) {
                mvc.perform(get("/api/" + resource.getKey())
                    .param(field + "Eq", values.get(field).toString())
                    .with(httpBasic("test", "test-password")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0]." + (resource.getKey().equals("importacoes") ? "idOriginal" : "id"))
                        .value(values.get(resource.getKey().equals("importacoes") ? "idOriginal" : "id")));
            }
        }
    }

    @Test
    void textIsPartialCaseInsensitiveAndExactMatchingIsAvailable() throws Exception {
        mvc.perform(get("/api/data-centers").param("nome", "ALPHA").with(httpBasic("test", "test-password")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(dcA));
        mvc.perform(get("/api/data-centers").param("nomeEq", "alpha").with(httpBasic("test", "test-password")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(0));
        mvc.perform(get("/api/data-centers").param("nomeEq", "CAMPUS ALPHA").with(httpBasic("test", "test-password")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void supportsInclusiveNumericDateAndIntegerRangesAndInLists() throws Exception {
        mvc.perform(get("/api/data-centers")
            .param("potenciaMwMin", "12.25").param("potenciaMwMax", "12.25")
            .param("dataValidacaoMin", "2026-01-01").param("dataValidacaoMax", "2026-12-31")
            .param("idIn", dcA + "," + dcB).with(httpBasic("test", "test-password")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(dcA));
        mvc.perform(get("/api/data-centers").param("idMin", String.valueOf(dcA))
            .param("idMax", String.valueOf(dcA)).with(httpBasic("test", "test-password")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(1));
        mvc.perform(get("/api/data-centers/filtro").param("cidades", "campinas,São Paulo")
            .with(httpBasic("test", "test-password")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(2));
        mvc.perform(get("/api/fontes").param("idIn", String.valueOf(fonteA), String.valueOf(fonteB))
            .with(httpBasic("test", "test-password")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    void filtersOptionalForeignKeysAndNullFieldsWithoutDroppingRows() throws Exception {
        mvc.perform(get("/api/indicadores-ambientais").param("fonteIdIsNull", "true")
            .with(httpBasic("test", "test-password"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.page.totalElements").value(1)).andExpect(jsonPath("$.content[0].dataCenterId").value(dcB));
        mvc.perform(get("/api/conteudos-pesquisa").param("dataCenterIdIsNull", "true")
            .param("dataPublicacaoIsNull", "true").with(httpBasic("test", "test-password")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(1));
        mvc.perform(get("/api/beneficios").param("dataCenterId", String.valueOf(dcA))
            .param("fonteIdIsNull", "false").with(httpBasic("test", "test-password")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void linkedSourcesAcceptAllSourceFieldsWhileKeepingParentRestriction() throws Exception {
        for (String field : FIELDS.get("fontes")) {
            mvc.perform(get("/api/data-centers/" + dcA + "/fontes")
                .param(field + "Eq", first.get("fontes").get(field).toString())
                .with(httpBasic("test", "test-password")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(fonteA));
        }
        mvc.perform(get("/api/data-centers/" + dcB + "/fontes").param("id", String.valueOf(fonteA))
            .with(httpBasic("test", "test-password")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void filteringHappensBeforePaginationAndCountUsesSameConditions() throws Exception {
        jdbc.update("INSERT INTO beneficio(data_center_id,tipo,descricao) VALUES (?,?,?)", dcA, "Emprego extra", "Extra");
        mvc.perform(get("/api/beneficios").param("dataCenterId", String.valueOf(dcA))
            .param("page", "1").param("size", "1").param("sort", "id,asc")
            .with(httpBasic("test", "test-password"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.page.totalElements").value(2)).andExpect(jsonPath("$.page.totalPages").value(2));
        mvc.perform(get("/api/importacoes").param("operador", "alpha").param("size", "1")
            .with(httpBasic("test", "test-password")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void mapAcceptsAllDataCenterFiltersButStillRequiresBothCoordinates() throws Exception {
        mvc.perform(get("/api/mapa").param("empresa", "ALPHA").param("dataValidacaoMin", "2026-01-01")
            .param("potenciaMwMax", "20").with(httpBasic("test", "test-password")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(dcA));
        jdbc.update("UPDATE data_center SET longitude = NULL WHERE id = ?", dcA);
        mvc.perform(get("/api/mapa").param("nome", "Alpha").with(httpBasic("test", "test-password")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void dashboardFiltersRowsBeforeGroupingAndAggregatesAfterGrouping() throws Exception {
        jdbc.update("INSERT INTO data_center(nome,cidade,estado,potencia_mw) VALUES ('Terceiro','Campinas','SP',5)");
        mvc.perform(get("/api/dashboard").param("estado", "sp").param("quantidadeDataCentersMin", "2")
            .param("potenciaTotalMwMin", "17.25").param("potenciaTotalMwMax", "17.25")
            .with(httpBasic("test", "test-password")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].cidade").value("Campinas")).andExpect(jsonPath("$[0].quantidadeDataCenters").value(2))
            .andExpect(jsonPath("$[0].potenciaTotalMw").value(17.25));
        mvc.perform(get("/api/dashboard").param("empresa", "Alpha").param("dataValidacaoMin", "2026-01-01")
            .param("quantidadeDataCenters", "1").with(httpBasic("test", "test-password")))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].potenciaTotalMw").value(12.25));
    }

    @Test
    void percentUnderscoreAndApostrophesAreLiteralAndNotSqlInjection() throws Exception {
        jdbc.update("UPDATE data_center SET nome = 'Rack 100%_Alpha' WHERE id = ?", dcA);
        mvc.perform(get("/api/data-centers").param("nome", "%_").with(httpBasic("test", "test-password")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(1));
        mvc.perform(get("/api/importacoes").param("operador", "' OR 1=1 --").with(httpBasic("test", "test-password")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(0));
        mvc.perform(get("/api/dashboard").param("nome", "%_").with(httpBasic("test", "test-password")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void invalidFiltersReturnSpecific400InsteadOfBeingSilentlyIgnored() throws Exception {
        for (String[] invalid : new String[][] {
            {"naoExiste", "x"}, {"id", "abc"}, {"potenciaMwMin", "abc"},
            {"dataValidacao", "13/10/2026"}, {"nomeMin", "abc"},
            {"latitudeIsNull", "sim"}, {"idIn", "1,abc"}, {"estadoIn", "SP,"}
        }) {
            mvc.perform(get("/api/data-centers").param(invalid[0], invalid[1]).with(httpBasic("test", "test-password")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail", containsString(invalid[0])));
        }
        mvc.perform(get("/api/data-centers").param("nome", "Alpha", "Beta").with(httpBasic("test", "test-password")))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/importacoes").param("latitudeMin", "10").with(httpBasic("test", "test-password")))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/data-centers").param("empresa", " ").with(httpBasic("test", "test-password")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(2));
    }

    private Map<String, Object> dataCenter(String name, String city, String state, String power, String date) {
        return map("nome", "Campus " + name, "empresa", "Operador " + name, "cidade", city, "estado", state,
            "pais", name.equals("Alpha") ? "Brasil" : "Portugal", "latitude", new BigDecimal(name.equals("Alpha") ? "-22.9" : "-23.4"),
            "longitude", new BigDecimal(name.equals("Alpha") ? "-47.1" : "-46.2"),
            "descricao", "Descrição " + name, "potenciaMw", new BigDecimal(power), "areaM2", new BigDecimal(name.equals("Alpha") ? "12000" : "20000"),
            "tipoInstalacao", "Campus " + name, "statusValidacao", name.equals("Alpha") ? "VALIDADO" : "PENDENTE",
            "dataValidacao", Date.valueOf(date));
    }
    private Map<String, Object> source(String name, String date) {
        return map("instituicao", "Instituto " + name, "titulo", "Relatório " + name,
            "url", "https://example.com/" + name, "dataPublicacao", Date.valueOf(date), "dataConsulta", Date.valueOf(date));
    }
    private Map<String, Object> map(Object... entries) {
        var result = new LinkedHashMap<String, Object>();
        for (int i = 0; i < entries.length; i += 2) result.put((String) entries[i], entries[i + 1]);
        return result;
    }
    private int insert(String table, Map<String, Object> values) {
        String columns = String.join(",", values.keySet().stream()
            .map(key -> key.replaceAll("([A-Z])", "_$1").toLowerCase(Locale.ROOT)).toList());
        String sql = "INSERT INTO " + table + "(" + columns + ") VALUES (" + String.join(",", Collections.nCopies(values.size(), "?")) + ")";
        if (table.equals("data_center_importacao")) { jdbc.update(sql, values.values().toArray()); return 0; }
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, new String[]{"id"});
            int index = 1;
            for (Object value : values.values()) statement.setObject(index++, value);
            return statement;
        }, keys);
        return Objects.requireNonNull(keys.getKey()).intValue();
    }
}
