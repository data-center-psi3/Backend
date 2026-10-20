package data_centers.brasil;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @Transactional
class RadarIntegrationTests {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    MockMvc mvc;
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.update("DELETE FROM data_center_importacao");
        row("1", " ACME  Cloud ", "São José", " sp ", "3509502", "10", "2", null, "-22,9", "-47.1", "Campus");
        row("2", "acme cloud", "Campinas", "SP", "3509502", "0", null, "3", "91", "0", "Campus");
        row("3", "Beta", "São José", "SC", null, "20", "4", "2", "-27", "-48", "Edge");
        row("4", "Não informado", "Sao Jose", "SC", null, "10+", "—", "-1", null, null, "—");
        row("5", null, "São José", "SP", null, null, "0", "2.5", "-90", "180", "Campus");
        row("6", "—", " ", "XX", "0000000", "não informado", null, null, "x", "1", null);
    }
    void row(String id, String op, String city, String uf, String ibge, String net, String ix, String carriers,
        String lat, String lon, String type) {
        jdbc.update("""
            INSERT INTO data_center_importacao(id_original,operador,unidade,municipio,uf,codigo_ibge,
            redes_conectadas,ix_conectados,carriers,latitude,longitude,tipo_instalacao,precisao_geo)
            VALUES (?,?, 'Unidade',?,?,?,?,?,?,?,?,?, 'Município')
            """, id, op, city, uf, ibge, net, ix, carriers, lat, lon, type);
    }
    ResultActions call(String path) throws Exception {
        return mvc.perform(get("/api/radar" + path).with(httpBasic("test", "test-password")));
    }
    @Test void overviewUsesRowsDistinctKeysAndValidNumbers() throws Exception {
        call("/overview").andExpect(status().isOk()).andExpect(jsonPath("$.totalInstalacoes").value(6))
            .andExpect(jsonPath("$.totalOperadores").value(2)).andExpect(jsonPath("$.totalMunicipios").value(3))
            .andExpect(jsonPath("$.totalEstados").value(2)).andExpect(jsonPath("$.instalacoesComCoordenadas").value(3))
            .andExpect(jsonPath("$.totalRedesRegistradas").value(30)).andExpect(jsonPath("$.mediaRedesPorInstalacao").value(10))
            .andExpect(jsonPath("$.totalIxRegistrados").value(6)).andExpect(jsonPath("$.mediaIxPorInstalacao").value(2))
            .andExpect(jsonPath("$.totalCarriersRegistrados").value(5)).andExpect(jsonPath("$.mediaCarriersPorInstalacao").value(2.5))
            .andExpect(jsonPath("$.instalacoesComConectividade").value(4));
    }
    @Test void stateShareKeepsUnfilteredNationalDenominator() throws Exception {
        call("/states?region=Sudeste&operator=acme cloud&installationType=Campus")
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].totalInstalacoes").value(2))
            .andExpect(jsonPath("$[0].totalOperadores").value(1))
            .andExpect(jsonPath("$[0].participacaoNacional").value(33.33));
        call("/states/sp").andExpect(status().isOk()).andExpect(jsonPath("$.nome").value("São Paulo"))
            .andExpect(jsonPath("$.participacaoNacional").value(50))
            .andExpect(jsonPath("$.operadores[0].totalInstalacoes").value(2))
            .andExpect(jsonPath("$.municipios[0].codigoIbge").value("3509502"));
    }
    @Test void combinedFiltersAndDistinctOperatorRanking() throws Exception {
        call("/overview?uf=sp&region=sudeste&operator=Acme Cloud&installationType=campus")
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalInstalacoes").value(2));
        call("/operators/ranking?limit=1").andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].totalInstalacoes").value(2));
        call("/overview?uf=SP&region=Sul").andExpect(status().isOk()).andExpect(jsonPath("$.totalInstalacoes").value(0))
            .andExpect(jsonPath("$.mediaRedesPorInstalacao").isEmpty()).andExpect(jsonPath("$.totalRedesRegistradas").isEmpty());
    }
    @Test void citiesGroupByIbgeAndAccentNormalizedFallback() throws Exception {
        call("/states/SP/cities").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].totalInstalacoes").value(2)).andExpect(jsonPath("$[0].totalOperadores").value(1));
        call("/cities/ranking?sortBy=networks&direction=desc&limit=1").andExpect(status().isOk())
            .andExpect(jsonPath("$[0].uf").value("SC")).andExpect(jsonPath("$[0].totalRedesRegistradas").value(20));
        call("/states?sortBy=installations&direction=asc").andExpect(status().isOk()).andExpect(jsonPath("$[0].uf").value("SC"));
        call("/states?sortBy=networks").andExpect(status().isOk()).andExpect(jsonPath("$[0].uf").value("SC"));
    }
    @Test void mapExcludesInvalidCoordinatesAndPreservesPrecision() throws Exception {
        call("/map").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].precisaoGeo").value("Município"));
        call("/map?uf=SP&operator=acme cloud&installationType=Campus").andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].latitude").value(-22.9));
        call("/map?uf=SP").andExpect(status().isOk()).andExpect(jsonPath("$[1].latitude").value(-90))
            .andExpect(jsonPath("$[1].longitude").value(180));
    }
    @Test void filtersReflectOnlyExistingValues() throws Exception {
        call("/filters?region=Sudeste").andExpect(status().isOk()).andExpect(jsonPath("$.states.length()").value(1))
            .andExpect(jsonPath("$.operators.length()").value(1)).andExpect(jsonPath("$.regions[0]").value("Sudeste"));
    }
    @Test void invalidArgumentsAndMissingState() throws Exception {
        for (String path : new String[]{"/states/XX", "/map?uf=XX", "/states?sortBy=drop", "/states?direction=up",
            "/overview?region=Atlantis", "/operators/ranking?limit=0", "/cities/ranking?limit=1001", "/states/SP?uf=SC"})
            call(path).andExpect(status().isBadRequest());
        call("/states/AC").andExpect(status().isNotFound());
        call("/states/AC/cities").andExpect(status().isNotFound());
        mvc.perform(get("/api/radar/overview")).andExpect(status().isUnauthorized());
    }
    @Test void emptyDatasetReturnsEmptyCollectionsAndAbsentMeasures() throws Exception {
        jdbc.update("DELETE FROM data_center_importacao");
        call("/overview").andExpect(status().isOk()).andExpect(jsonPath("$.totalInstalacoes").value(0))
            .andExpect(jsonPath("$.totalRedesRegistradas").isEmpty());
        for (String path : new String[]{"/states", "/map", "/cities/ranking", "/operators/ranking"})
            call(path).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }
}
