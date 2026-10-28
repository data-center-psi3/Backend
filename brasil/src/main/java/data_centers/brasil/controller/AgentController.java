package data_centers.brasil.controller;

import data_centers.brasil.service.AgentService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/agent")
public class AgentController {


        private final AgentService agentService;

        public AgentController(AgentService agentService) {
            this.agentService = agentService;
        }

        @PostMapping("/chat")
        public Map<String, String> chat(
                @RequestBody Map<String, String> request
        ) {

            String pergunta = request.get("message");

            if (pergunta == null || pergunta.isBlank()) {
                throw new IllegalArgumentException(
                        "A mensagem não pode estar vazia."
                );
            }

            String resposta = agentService.perguntar(pergunta);

            return Map.of("response", resposta);
        }
    }
