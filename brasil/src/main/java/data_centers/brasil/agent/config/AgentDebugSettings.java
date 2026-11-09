package data_centers.brasil.agent.config;

import org.springframework.stereotype.Component;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.beans.factory.annotation.Value;

@Component
public class AgentDebugSettings {
    private final boolean enabled;
    private final Environment environment;
    public AgentDebugSettings(Environment environment,@Value("${app.agent.debug-sql-enabled:false}") boolean enabled) {
        this.environment=environment; this.enabled=enabled;
    }
    public boolean sqlAllowed() {
        return enabled && environment.acceptsProfiles(Profiles.of("dev","local","test"))
            && !environment.acceptsProfiles(Profiles.of("prod","production"));
    }
}
