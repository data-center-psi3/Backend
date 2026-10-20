package data_centers.brasil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class OpenApiIntegrationTests {
    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void documentationIsPublicAndDescribesBasicAuthentication() throws Exception {
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.openapi").exists())
            .andExpect(jsonPath("$.paths['/api/data-centers']").exists())
            .andExpect(jsonPath("$.components.securitySchemes.basicAuth.type").value("http"))
            .andExpect(jsonPath("$.components.securitySchemes.basicAuth.scheme").value("basic"))
            .andExpect(jsonPath("$.security[0].basicAuth").isArray());
        mvc.perform(get("/v3/api-docs.yaml"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("openapi:")));
        mvc.perform(get("/v3/api-docs/swagger-config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.url").value("/v3/api-docs"));
    }

    @Test
    void swaggerPageAndAssetsArePublic() throws Exception {
        mvc.perform(get("/swagger-ui.html"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", "/swagger-ui/index.html"));
        mvc.perform(get("/swagger-ui/index.html"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Swagger UI")));
        mvc.perform(get("/swagger-ui/swagger-ui-bundle.js")).andExpect(status().isOk());
        mvc.perform(get("/swagger-ui/swagger-initializer.js")).andExpect(status().isOk());
    }

    @Test
    void apiStillRequiresBasicAuthentication() throws Exception {
        mvc.perform(get("/api/data-centers")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/data-centers").with(httpBasic("test", "test-password")))
            .andExpect(status().isOk());
    }
}
