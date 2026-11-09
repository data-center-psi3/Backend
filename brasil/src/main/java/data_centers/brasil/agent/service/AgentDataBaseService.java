package data_centers.brasil.agent.service;

import data_centers.brasil.service.DataCenterService;
import data_centers.brasil.service.IndicadorAmbientalService;
import data_centers.brasil.service.BeneficioService;
import data_centers.brasil.service.ConteudoPesquisaService;
import data_centers.brasil.service.FonteService;
import data_centers.brasil.service.DataCenterFonteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import data_centers.brasil.agent.repository.AgentQueryRepository;
import org.springframework.util.LinkedMultiValueMap;
import java.util.*;
import data_centers.brasil.filter.*;

/** Only this allowlist can be invoked by the model; never accepts SQL or filter expressions. */
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class AgentDataBaseService {
    private final DataCenterService dataCenters;
    private final IndicadorAmbientalService indicadores;
    private final BeneficioService beneficios;
    private final ConteudoPesquisaService pesquisas;
    private final FonteService fontes;
    private final DataCenterFonteService fontesVinculadas;
    private final AgentQueryRepository consultas;

    public enum Operacao { CONTAR, LISTAR, ESTADOS, MUNICIPIOS, OPERADORES, INDICADORES, BENEFICIOS, PESQUISAS, NOTICIAS, FONTES }
    @org.springframework.beans.factory.annotation.Value("${agent.municipios.max-records:1000}")
    private int maxMunicipios=1000;
    @org.springframework.beans.factory.annotation.Value("${agent.municipios.max-pages:50}")
    private int maxMunicipioPages=50;
    private static final Set<String> KEYS = Set.of("operacao", "estado", "cidade", "empresa", "nome", "dataCenterId", "fonteId", "termo", "pagina", "limite", "todos", "ranking");

    public Map<String, Object> executar(Map<?, ?> args) {
        if (!KEYS.containsAll(args.keySet())) throw new IllegalArgumentException("Argumento desconhecido.");
        Operacao op;
        try { op = Operacao.valueOf(text(args, "operacao")); }
        catch (RuntimeException e) { throw new IllegalArgumentException("Operação inválida."); }
        int page = integer(args, "pagina", 0, 0, 1000);
        int size = integer(args, "limite", 20, 1, 50);
        Integer dc = args.containsKey("dataCenterId") ? integer(args, "dataCenterId", 1, 1, Integer.MAX_VALUE) : null;
        Integer source = args.containsKey("fonteId") ? integer(args, "fonteId", 1, 1, Integer.MAX_VALUE) : null;
        var filters = new LinkedMultiValueMap<String, String>();
        for (String key : List.of("estado", "cidade", "empresa", "nome")) {
            String value = text(args, key);
            if (value != null) {
                if (key.equals("estado")) {
                    value = TextNormalization.state(value);
                }
                filters.add(key + "Eq", value);
            }
        }
        String term = text(args, "termo");
        // Reject irrelevant arguments rather than silently returning an unfiltered answer.
        if (op != Operacao.CONTAR && op != Operacao.LISTAR && op != Operacao.OPERADORES && op != Operacao.MUNICIPIOS && !filters.isEmpty()) throw new IllegalArgumentException("Filtros geográficos e de instalação incompatíveis.");
        if ((args.containsKey("todos") || args.containsKey("ranking")) && op != Operacao.MUNICIPIOS) throw new IllegalArgumentException("todos/ranking exigem MUNICIPIOS.");
        if (dc != null && !Set.of(Operacao.LISTAR, Operacao.INDICADORES, Operacao.BENEFICIOS, Operacao.PESQUISAS, Operacao.FONTES).contains(op)) throw new IllegalArgumentException("dataCenterId incompatível.");
        if (source != null && op != Operacao.FONTES) throw new IllegalArgumentException("fonteId incompatível.");
        if (term != null && !Set.of(Operacao.INDICADORES, Operacao.BENEFICIOS, Operacao.PESQUISAS, Operacao.NOTICIAS).contains(op)) throw new IllegalArgumentException("termo incompatível.");
        var pageable = PageRequest.of(page, size, Sort.by("id"));
        Object result = switch (op) {
            case CONTAR -> Map.of("total", dataCenters.count(filters), "filtros", filters);
            case LISTAR -> {
                if (dc != null) filters.add("id", dc.toString());
                yield paged(dataCenters.list(filters, pageable));
            }
            case ESTADOS -> consultas.estados(size, (long) page * size);
            case MUNICIPIOS -> municipios(page,size,filters,flag(args,"todos"),flag(args,"ranking"));
            case OPERADORES -> consultas.operadores(size, (long) page * size, filters);
            case INDICADORES, BENEFICIOS, PESQUISAS -> {
                if (dc != null) filters.add("dataCenterId", dc.toString());
                if (term != null) filters.add(op == Operacao.PESQUISAS ? "conteudo" : "tipo", term);
                yield switch (op) {
                    case INDICADORES -> paged(indicadores.list(filters, pageable));
                    case BENEFICIOS -> paged(beneficios.list(filters, pageable));
                    default -> paged(pesquisas.list(filters, pageable));
                };
            }
            case FONTES -> {
                if (source != null) filters.add("id", source.toString());
                yield dc == null ? paged(fontes.list(filters, pageable)) : paged(fontesVinculadas.list(dc, filters, pageable));
            }
            case NOTICIAS -> consultas.noticias(term, size, (long) page * size);
        };
        // Query identity stays in tool metadata, never in the user-facing text.
        var consulta = new TreeMap<String, Object>();
        args.forEach((key, value) -> {
            if (!Set.of("operacao", "pagina", "limite").contains(key))
                consulta.put((String) key, value instanceof String text ? (key.equals("estado") ? TextNormalization.state(text) : TextNormalization.normalize(text)) : value);
        });
        return Map.of("consultadoEm", java.time.Instant.now().toString(), "consulta", consulta, "origem", "PostgreSQL DataTerra", "operacao", op.name(), "resultado", result,
            "pagina", page, "limite", size, "limitacoes", "Registros cadastrados, não censo nacional. Ausência de registros não implica ausência no mundo real. Valores sem classificação explícita não devem ser chamados de medidos. MUNICIPIOS permite todos=true com limites de segurança e informa completo/hasNext; outras listagens são paginadas. Use FONTES com fonteId ou dataCenterId para documentação.");
    }

    public long contarDataCenters() { return dataCenters.count(new LinkedMultiValueMap<>()); }
    private Map<String,Object> municipios(int page,int size,LinkedMultiValueMap<String,String> filters,boolean all,boolean ranking) {
        var records=new ArrayList<data_centers.brasil.agent.dto.MunicipioCount>();
        int start=all?0:page, pages=0;
        long total=0;
        int budget=Math.max(1,maxMunicipios), pageBudget=Math.max(1,maxMunicipioPages);
        do {
            int fetch=all?Math.min(size,budget-records.size()):size;
            var result=consultas.municipios(fetch,all?(long)records.size():(long)page*size,filters,ranking);
            total=((Number)result.get("totalMunicipios")).longValue();
            @SuppressWarnings("unchecked") var rows=(List<data_centers.brasil.agent.dto.MunicipioCount>)result.get("registros");
            records.addAll(rows); pages++;
            if(!all || rows.isEmpty() || records.size()>=total) break;
        } while(records.size()<budget && pages<pageBudget);
        long end=(all?0:(long)page*size)+records.size();
        var result=new LinkedHashMap<String,Object>();
        result.put("registros",List.copyOf(records)); result.put("totalMunicipios",total); result.put("totalElements",total);
        result.put("totalPages",(total+size-1)/size); result.put("page",start); result.put("size",size);
        result.put("hasNext",end<total); result.put("retornados",records.size()); result.put("completo",start==0 && end>=total);
        result.put("paginasConsultadas",pages); result.put("totalInstalacoesRetornadas",records.stream().mapToLong(data_centers.brasil.agent.dto.MunicipioCount::quantidade).sum());
        if(all && end<total) result.put("alternativa","Limite de segurança atingido. Continue com MUNICIPIOS, todos=false, pagina="+(end/size)+", limite="+size+"; a página de continuação pode repetir registros se o limite não for múltiplo do tamanho da página.");
        return result;
    }
    private static boolean flag(Map<?,?> args,String key) {
        if(!args.containsKey(key)) return false;
        if(!(args.get(key) instanceof Boolean value)) throw new IllegalArgumentException("Booleano inválido: "+key);
        return value;
    }
    public Map<String, Object> consultarResumo() { return executar(Map.of("operacao", "CONTAR")); }
    private static Map<String, Object> paged(Page<?> p) {
        return Map.of("registros", p.getContent(), "total", p.getTotalElements(), "temProximaPagina", p.hasNext());
    }
    private static String text(Map<?, ?> args, String key) {
        if (!args.containsKey(key)) return null;
        if (!(args.get(key) instanceof String s) || s.isBlank() || s.length() > 200) throw new IllegalArgumentException("Texto inválido: " + key);
        return s.trim();
    }
    private static int integer(Map<?, ?> args, String key, int fallback, int min, int max) {
        if (!args.containsKey(key)) return fallback;
        if (!(args.get(key) instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue() != n.longValue() || n.longValue() < min || n.longValue() > max)
            throw new IllegalArgumentException("Inteiro fora do limite: " + key);
        return n.intValue();
    }
}
