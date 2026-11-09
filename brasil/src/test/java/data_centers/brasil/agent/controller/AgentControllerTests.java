package data_centers.brasil.agent.controller;

import data_centers.brasil.agent.controller.AgentController;
import data_centers.brasil.agent.service.AgentService;
import data_centers.brasil.agent.dto.AgentChatResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AgentControllerTests {
    @Test void existingEndpointAndJsonContractRemainCompatible() throws Exception {
        var service=mock(AgentService.class);
        when(service.perguntarComFonte("Pergunta",null,"test")).thenReturn(new AgentChatResponse("Resposta", "banco_de_dados", List.of("select count(*) from data_center where estado=?")));
        var mvc=MockMvcBuilders.standaloneSetup(new AgentController(service)).build();
        mvc.perform(post("/api/agent/chat").principal(() -> "test").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Pergunta\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.response").value("Resposta"))
            .andExpect(jsonPath("$.fonte").value("banco_de_dados"))
            .andExpect(jsonPath("$.consulta[0]").value("select count(*) from data_center where estado=?"))
            .andExpect(jsonPath("$.consultaOrigem").value("atual"));
    }
    @Test void conceptualAnswerReturnsEmptyQueryList() throws Exception {
        var service=mock(AgentService.class);
        when(service.perguntarComFonte("Conceito",null,"test")).thenReturn(new AgentChatResponse("Resposta", "conhecimento_geral_gemini", List.of()));
        var mvc=MockMvcBuilders.standaloneSetup(new AgentController(service)).build();
        mvc.perform(post("/api/agent/chat").principal(() -> "test").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Conceito\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.consulta").isEmpty())
            .andExpect(jsonPath("$.consultaOrigem").value("nenhuma"));
    }

}
