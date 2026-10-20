package data_centers.brasil;

import data_centers.brasil.repository.DataCenterRepository;
import data_centers.brasil.service.PopulacaoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties =
    "spring.datasource.url=jdbc:h2:mem:carga-runner;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles({"test", "popular"})
class CargaInicialRunnerTests {
    @Autowired DataCenterRepository dataCenters;
    @Autowired PopulacaoService service;

    @Test
    void profilePopulatesDatabaseWithoutWebServerAndCanBeRepeated() {
        assertEquals(352, dataCenters.count());
        assertEquals(352, service.populateClasspath().registrosIgnorados());
        assertEquals(352, dataCenters.count());
    }
}
