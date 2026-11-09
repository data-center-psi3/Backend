package data_centers.brasil.agent.dto;

import java.util.*;

public record AgentChatResponse(String response,String fonte,List<String> consulta,UUID conversationId,String consultaOrigem,Object chartData,Object visualization) {
    public AgentChatResponse(String response,String fonte,List<String> consulta,UUID conversationId,String consultaOrigem,Object chartData) {
        this(response,fonte,consulta,conversationId,consultaOrigem,chartData,chartData);
    }
    public AgentChatResponse(String response,String fonte,List<String> consulta,UUID conversationId,String consultaOrigem) {
        this(response,fonte,consulta,conversationId,consultaOrigem,null);
    }
    public AgentChatResponse { consulta=List.copyOf(consulta); }
    public AgentChatResponse(String response,String fonte,List<String> consulta,UUID conversationId) {
        this(response,fonte,consulta,conversationId,consulta.isEmpty()?"nenhuma":"atual");
    }
    public AgentChatResponse(String response,String fonte,List<String> consulta) { this(response,fonte,consulta,null); }
}
