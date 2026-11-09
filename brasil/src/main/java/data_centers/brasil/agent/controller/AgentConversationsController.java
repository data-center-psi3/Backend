package data_centers.brasil.agent.controller;

import data_centers.brasil.agent.memory.AgentConversationService;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/agent/conversations")
public class AgentConversationsController {
    private final AgentConversationService memory;
    public AgentConversationsController(AgentConversationService memory) { this.memory=memory; }
    @GetMapping
    public List<AgentConversationService.Summary> list(Principal principal) {
        return memory.list(principal==null?null:principal.getName());
    }
    @GetMapping("/{id}")
    public AgentConversationService.Conversation read(@PathVariable UUID id,Principal principal) {
        return memory.read(id,principal==null?null:principal.getName());
    }
}
