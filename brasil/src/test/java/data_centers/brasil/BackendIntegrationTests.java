package data_centers.brasil;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest @ActiveProfiles("test") @Transactional
class BackendIntegrationTests {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    MockMvc mvc;
    @BeforeEach void setup() { mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }
    Integer dc() { jdbc.update("INSERT INTO data_center(nome,cidade,estado,potencia_mw,latitude,longitude) VALUES ('DC Teste','Campinas','SP',10.25,-22.9,-47.1)"); return jdbc.queryForObject("SELECT id FROM data_center WHERE nome='DC Teste'", Integer.class); }
    Integer fonte() { jdbc.update("INSERT INTO fonte(instituicao,titulo,data_consulta) VALUES ('Instituto','Relatório',CURRENT_DATE)"); return jdbc.queryForObject("SELECT id FROM fonte WHERE titulo='Relatório'", Integer.class); }
    @Test void authenticationRequired() throws Exception { mvc.perform(get("/api/data-centers")).andExpect(status().isUnauthorized()); }
    @Test void createReadUpdateDeleteAndValidation() throws Exception {
        mvc.perform(post("/api/data-centers").with(httpBasic("test","test-password")).contentType("application/json")
            .content("{\"nome\":\"DC Novo\",\"cidade\":\"Campinas\",\"estado\":\"SP\",\"latitude\":-22.9}"))
            .andExpect(status().isCreated()).andExpect(header().exists("Location")).andExpect(jsonPath("$.pais").value("Brasil"));
        int id = jdbc.queryForObject("SELECT id FROM data_center WHERE nome='DC Novo'", Integer.class);
        mvc.perform(get("/api/data-centers/" + id).with(httpBasic("test","test-password"))).andExpect(status().isOk());
        mvc.perform(put("/api/data-centers/" + id).with(httpBasic("test","test-password")).contentType("application/json")
            .content("{\"nome\":\"Atualizado\",\"cidade\":\"Hortolândia\",\"estado\":\"SP\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.nome").value("Atualizado"));
        mvc.perform(delete("/api/data-centers/" + id).with(httpBasic("test","test-password"))).andExpect(status().isNoContent());
        mvc.perform(get("/api/data-centers/" + id).with(httpBasic("test","test-password"))).andExpect(status().isNotFound());
        mvc.perform(post("/api/data-centers").with(httpBasic("test","test-password")).contentType("application/json")
            .content("{\"nome\":\"\",\"cidade\":\"Campinas\",\"estado\":\"SP\",\"latitude\":91}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors").isArray());
    }
    @Test void optionalAndRequiredRelationships() throws Exception {
        int dc = dc(); int fonte = fonte();
        mvc.perform(post("/api/indicadores-ambientais").with(httpBasic("test","test-password")).contentType("application/json")
            .content("{\"dataCenterId\":" + dc + ",\"tipo\":\"PUE\",\"valor\":1.25}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.fonteId").doesNotExist());
        mvc.perform(post("/api/beneficios").with(httpBasic("test","test-password")).contentType("application/json")
            .content("{\"dataCenterId\":" + dc + ",\"tipo\":\"Emprego\",\"descricao\":\"Empregos locais\",\"fonteId\":" + fonte + "}"))
            .andExpect(status().isCreated());
        mvc.perform(post("/api/conteudos-pesquisa").with(httpBasic("test","test-password")).contentType("application/json")
            .content("{\"titulo\":\"Pesquisa\",\"conteudo\":\"Texto\",\"fonteId\":" + fonte + "}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.dataCenterId").doesNotExist());
        mvc.perform(post("/api/conteudos-pesquisa").with(httpBasic("test","test-password")).contentType("application/json")
            .content("{\"titulo\":\"Pesquisa\",\"conteudo\":\"Texto\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/indicadores-ambientais").with(httpBasic("test","test-password")).contentType("application/json")
            .content("{\"dataCenterId\":2147483647,\"tipo\":\"PUE\"}")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/data-centers/" + dc).with(httpBasic("test","test-password"))).andExpect(status().isConflict());
    }
    @Test void sourceLinksAreIdempotentAndRemovable() throws Exception {
        int dc = dc(); int fonte = fonte();
        String path = "/api/data-centers/" + dc + "/fontes/" + fonte;
        for (int i = 0; i < 2; i++) mvc.perform(put(path).with(httpBasic("test","test-password"))).andExpect(status().isNoContent());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM data_center_fonte", Integer.class));
        mvc.perform(get("/api/data-centers/" + dc + "/fontes").with(httpBasic("test","test-password")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(fonte));
        mvc.perform(delete(path).with(httpBasic("test","test-password"))).andExpect(status().isNoContent());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM data_center_fonte", Integer.class));
    }
    @Test void mapFiltersAndDashboard() throws Exception {
        dc();
        jdbc.update("INSERT INTO data_center(nome,cidade,estado) VALUES ('Sem coordenadas','Sumaré','SP')");
        mvc.perform(get("/api/mapa").with(httpBasic("test","test-password"))).andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(1));
        mvc.perform(get("/api/data-centers/filtro?cidades=Campinas&estado=sp").with(httpBasic("test","test-password")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(1));
        mvc.perform(get("/api/dashboard").with(httpBasic("test","test-password")))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].cidade").value("Campinas")).andExpect(jsonPath("$[0].potenciaTotalMw").value(10.25));
    }
    @Test @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void csvPreservesRawDataAndRejectsMalformedHeaders() throws Exception {
        String header = "id;operador;unidade;municipio;uf;codigo_ibge;endereco;latitude;longitude;precisao_geo;tipo_instalacao;status_operacional;inicio_operacao;potencia_ti_mw_unidade;potencia_campus_mw;area_m2_unidade;redes_conectadas;ix_conectados;carriers;voltada_ia;resfriamento;fonte_energia;pue_declarado;wue_declarado;certificacoes;robustez_evidencia;fonte_url;data_levantamento";
        String row = "BR-1;Operador;\"Unidade; A\";Campinas;SP;3509502;Rua A;-22,9;-47,1;" + ";".repeat(18);
        mvc.perform(multipart("/api/importacoes").file(new MockMultipartFile("arquivo","dados.csv","text/csv",(header + "\n" + row + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8)))
            .with(httpBasic("test","test-password"))).andExpect(status().isOk()).andExpect(jsonPath("$.linhasImportadas").value(1));
        assertEquals("-22,9", jdbc.queryForObject("SELECT latitude FROM data_center_importacao", String.class));
        mvc.perform(get("/api/importacoes").with(httpBasic("test","test-password"))).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].unidade").value("Unidade; A"));
        mvc.perform(multipart("/api/importacoes").file(new MockMultipartFile("arquivo","bad.csv","text/csv","id;nome\n1;x".getBytes()))
            .with(httpBasic("test","test-password"))).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/importacoes").file(new MockMultipartFile("arquivo","partial.csv","text/csv",(header + "\n" + row + "\nbad\n").getBytes()))
            .with(httpBasic("test","test-password"))).andExpect(status().isBadRequest());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM data_center_importacao", Integer.class));
        jdbc.update("DELETE FROM data_center_importacao");
    }
}
