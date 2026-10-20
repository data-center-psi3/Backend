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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class CorsIntegrationTests {
    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void preflightFromBothFrontendOriginsSucceedsWithoutAuthentication() throws Exception {
        for (String origin : new String[]{"http://localhost:5173", "http://127.0.0.1:5173"}) {
            mvc.perform(options("/api/data-centers")
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Authorization, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", origin))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("Authorization")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("Content-Type")));
        }
    }

    @Test
    void authenticatedFrontendRequestReceivesCorsHeaders() throws Exception {
        mvc.perform(get("/api/data-centers")
            .header("Origin", "http://localhost:5173")
            .with(httpBasic("test", "test-password")))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
            .andExpect(header().string("Access-Control-Expose-Headers", containsString("Location")));
    }

    @Test
    void frontendCanReadAuthenticationFailureWithoutCorsBlockingIt() throws Exception {
        mvc.perform(get("/api/data-centers").header("Origin", "http://localhost:5173"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void rejectsPreflightFromUnconfiguredOrigin() throws Exception {
        mvc.perform(options("/api/data-centers")
            .header("Origin", "https://unconfigured.example")
            .header("Access-Control-Request-Method", "GET")
            .header("Access-Control-Request-Headers", "Authorization"))
            .andExpect(status().isForbidden())
            .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void corsDoesNotEnableUnsupportedHttpMethods() throws Exception {
        mvc.perform(options("/api/data-centers/1")
            .header("Origin", "http://localhost:5173")
            .header("Access-Control-Request-Method", "PATCH")
            .header("Access-Control-Request-Headers", "Authorization, Content-Type"))
            .andExpect(status().isForbidden());
    }
}
