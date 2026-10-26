package data_centers.brasil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "app.cors.allowed-origins=https://frontend.example",
    "spring.datasource.url=jdbc:h2:mem:auth-tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
@ActiveProfiles("test")
class AuthIntegrationTests {
    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test void validatesCredentialsWithoutCreatingSession() throws Exception {
        mvc.perform(get("/api/auth/me").with(httpBasic("test", "test-password")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("test"))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(cookie().doesNotExist("JSESSIONID"));
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test void rejectsWrongPassword() throws Exception {
        mvc.perform(get("/api/auth/me").with(httpBasic("test", "wrong")))
            .andExpect(status().isUnauthorized());
    }

    @Test void healthIsPublic() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test void configuredFrontendCanAuthenticate() throws Exception {
        mvc.perform(options("/api/auth/me").header("Origin", "https://frontend.example")
            .header("Access-Control-Request-Method", "GET")
            .header("Access-Control-Request-Headers", "Authorization"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "https://frontend.example"));
        mvc.perform(get("/api/auth/me").header("Origin", "https://frontend.example")
            .with(httpBasic("test", "test-password")))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "https://frontend.example"));
    }
}
