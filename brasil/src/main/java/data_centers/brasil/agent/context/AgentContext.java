package data_centers.brasil.agent.context;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Loads the agent instructions once at startup, independently of business logic. */
@Component
public class AgentContext {
    private final String instructions;

    public AgentContext() {
        try {
            instructions = new ClassPathResource("agent/system-prompt.txt").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Não foi possível carregar o contexto inicial do agente.", ex);
        }
        if (instructions.isBlank()) throw new IllegalStateException("O contexto inicial do agente está vazio.");
    }

    public String instrucoes() { return instructions; }
}
