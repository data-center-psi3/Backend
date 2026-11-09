package data_centers.brasil.agent.controller;

import org.springframework.web.bind.annotation.*;
import data_centers.brasil.agent.memory.AgentConversationException;
import org.springframework.http.ProblemDetail;

@RestControllerAdvice(basePackageClasses=AgentController.class)
public class AgentExceptionHandler {
    @ExceptionHandler(AgentConversationException.class)
    public ProblemDetail conversation(AgentConversationException error) {
        var detail=ProblemDetail.forStatusAndDetail(error.getStatusCode(),error.getReason());
        if(error.conversationId()!=null) detail.setProperty("conversationId",error.conversationId());
        return detail;
    }
}
