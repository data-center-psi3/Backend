package data_centers.brasil.agent.controller;

import data_centers.brasil.agent.service.AgentService;
import data_centers.brasil.agent.dto.AgentChatResponse;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/agent")
public class AgentController {
    private final AgentService agentService;
    public AgentController(AgentService agentService) { this.agentService=agentService; }
    public record ChatRequest(String message,UUID conversationId) {}
    @PostMapping("/chat")
    public AgentChatResponse chat(@RequestBody ChatRequest request,Principal principal) {
        return agentService.perguntarComFonte(request.message(),request.conversationId(),principal==null?null:principal.getName());
    }
}
