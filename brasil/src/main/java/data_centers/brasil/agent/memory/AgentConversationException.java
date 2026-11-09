package data_centers.brasil.agent.memory;

import java.util.UUID;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

public class AgentConversationException extends ResponseStatusException {
    private final UUID conversationId;
    public AgentConversationException(HttpStatusCode status,String reason,UUID conversationId) {
        super(status,reason);this.conversationId=conversationId;
    }
    public UUID conversationId() { return conversationId; }
}
