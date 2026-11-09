package data_centers.brasil.agent.service;

import data_centers.brasil.agent.config.AgentDebugSettings;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AgentObservabilityTests {
    @Test void sqlNeedsExplicitFlagAndNonProductionProfile() {
        var env=new MockEnvironment();env.setActiveProfiles("dev");
        assertFalse(new AgentDebugSettings(env,false).sqlAllowed());
        assertTrue(new AgentDebugSettings(env,true).sqlAllowed());
        env.setActiveProfiles("prod","dev");assertFalse(new AgentDebugSettings(env,true).sqlAllowed());
        env.setActiveProfiles("production","test");assertFalse(new AgentDebugSettings(env,true).sqlAllowed());
        env.setActiveProfiles();assertFalse(new AgentDebugSettings(env,true).sqlAllowed());
    }
    @Test void structuredEventsHaveCorrelationAndRedactConfidentialFieldsAndErrors() {
        var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(AgentToolTelemetry.class);
        var appender=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();appender.start();logger.addAppender(appender);
        UUID id=UUID.randomUUID();
        try {
            new AgentToolTelemetry().record(id,"consultar_dataterra",Map.of("operacao","CONTAR","estado","SP","empresa","secret-company","termo","SELECT confidential"),
                Map.of("resultado",Map.of("total",3)),2_000_000,false,new IllegalArgumentException("secret-password"));
            String log=appender.list.getLast().getFormattedMessage();
            var node=tools.jackson.databind.json.JsonMapper.builder().build().readTree(log);
            assertEquals(id.toString(),node.get("conversationId").asString());assertEquals("CONTAR",node.get("operation").asString());
            assertEquals(3,node.get("resultCount").asInt());assertTrue(node.has("durationMs"));
            assertEquals("error",node.get("status").asString());assertEquals("IllegalArgumentException",node.get("errorType").asString());
            assertEquals("SP",node.get("filters").get("estado").asString());
            assertFalse(log.contains("secret-company"));assertFalse(log.contains("confidential"));assertFalse(log.contains("secret-password"));
        } finally { logger.detachAppender(appender);appender.stop(); }
    }
}
