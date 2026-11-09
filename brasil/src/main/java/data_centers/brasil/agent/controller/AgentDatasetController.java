package data_centers.brasil.agent.controller;

import data_centers.brasil.agent.memory.AgentConversationService;
import data_centers.brasil.agent.service.AgentQueryPaginationService;
import data_centers.brasil.agent.visualization.AgentDatasetService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
import java.security.Principal;

/** Dataset identifiers are scoped to an authenticated conversation, never globally addressable. */
@RestController
@RequestMapping("/api/agent/conversations/{conversationId}/datasets")
public class AgentDatasetController {
    private final AgentConversationService memory;
    private final AgentDatasetService datasets;
    private final AgentQueryPaginationService pagination;
    public AgentDatasetController(AgentConversationService memory,AgentDatasetService datasets,AgentQueryPaginationService pagination) {
        this.memory=memory;this.datasets=datasets;this.pagination=pagination;
    }
    @GetMapping("/{datasetId}")
    public Map<String,Object> page(@PathVariable UUID conversationId,@PathVariable String datasetId,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,Principal principal) {
        if(page<0 || page>1000 || size<1 || size>50) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Página ou tamanho inválido.");
        var session=memory.open(conversationId,principal==null?null:principal.getName());
        var dataset=datasets.datasets(memory.results(session)).stream().filter(d->d.id().equals(datasetId)).findFirst()
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Dataset não encontrado nesta conversa."));
        long offset=(long)page*size;
        if(dataset.complete()) {
            int start=(int)Math.min(offset,dataset.data().size()),end=Math.min(start+size,dataset.data().size());
            return Map.of("datasetId",datasetId,"data",dataset.data().subList(start,end),"page",page,"size",size,"totalElements",dataset.total(),"hasNext",end<dataset.total(),"refreshed",false);
        }
        var query=dataset.queries().getLast();
        if(!(query.get("_request") instanceof Map<?,?> request)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Reexecute a consulta para recuperar este dataset.");
        var args=new LinkedHashMap<String,Object>();request.forEach((k,v)->args.put((String)k,v));
        args.put("pagina",page);args.put("limite",size);args.remove("todos");
        var result=pagination.execute(args,false);
        var current=datasets.datasets(List.of(result)).getFirst();
        // Fail closed on an unexpectedly large page; no silent row truncation.
        if(tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsBytes(current.data()).length>1_000_000)
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"Página muito grande. Reduza size.");
        return Map.of("datasetId",datasetId,"data",current.data(),"page",page,"size",size,"totalElements",current.total(),"hasNext",current.total()<0?current.data().size()==size:offset+current.data().size()<current.total(),"refreshed",true);
    }
}
