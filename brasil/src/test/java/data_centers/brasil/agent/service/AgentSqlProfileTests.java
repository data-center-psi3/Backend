package data_centers.brasil.agent.service;

import data_centers.brasil.agent.config.AgentDebugSettings;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.*;

class AgentSqlProfileTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withUserConfiguration(AgentDebugSettings.class);

    @Test void developmentAndLocalProfilesEnableRawSqlUsingActualConfigurationFiles() {
        for (String profile : new String[]{"dev", "local"}) {
            runner.withPropertyValues("spring.profiles.active=" + profile)
                .run(context -> assertTrue(context.getBean(AgentDebugSettings.class).sqlAllowed()));
        }
    }

    @Test void explicitDisableIsRespectedInDevelopment() {
        runner.withPropertyValues("spring.profiles.active=dev", "AGENT_DEBUG_SQL_ENABLED=false")
            .run(context -> assertFalse(context.getBean(AgentDebugSettings.class).sqlAllowed()));
    }

    @Test void productionStillBlocksSqlEvenWithDevelopmentAndFlagEnabled() {
        runner.withPropertyValues("spring.profiles.active=dev,prod", "AGENT_DEBUG_SQL_ENABLED=true")
            .run(context -> assertFalse(context.getBean(AgentDebugSettings.class).sqlAllowed()));
    }

    @Test void defaultProfileDoesNotExposeSql() {
        runner.withPropertyValues("spring.profiles.active=default", "AGENT_DEBUG_SQL_ENABLED=false")
            .run(context -> assertFalse(context.getBean(AgentDebugSettings.class).sqlAllowed()));
    }
}
