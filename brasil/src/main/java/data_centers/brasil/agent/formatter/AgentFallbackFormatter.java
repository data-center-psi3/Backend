package data_centers.brasil.agent.formatter;

import data_centers.brasil.dto.*;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.*;

/** Deterministic presentation of already executed, bounded queries. No AI or database calls. */
@org.springframework.stereotype.Component
public final class AgentFallbackFormatter {
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final Map<String, String> ESTADOS = Map.ofEntries(
        Map.entry("AC", "Acre"), Map.entry("AL", "Alagoas"), Map.entry("AP", "Amapá"),
        Map.entry("AM", "Amazonas"), Map.entry("BA", "Bahia"), Map.entry("CE", "Ceará"),
        Map.entry("DF", "Distrito Federal"), Map.entry("ES", "Espírito Santo"), Map.entry("GO", "Goiás"),
        Map.entry("MA", "Maranhão"), Map.entry("MT", "Mato Grosso"), Map.entry("MS", "Mato Grosso do Sul"),
        Map.entry("MG", "Minas Gerais"), Map.entry("PA", "Pará"), Map.entry("PB", "Paraíba"),
        Map.entry("PR", "Paraná"), Map.entry("PE", "Pernambuco"), Map.entry("PI", "Piauí"),
        Map.entry("RJ", "Rio de Janeiro"), Map.entry("RN", "Rio Grande do Norte"), Map.entry("RS", "Rio Grande do Sul"),
        Map.entry("RO", "Rondônia"), Map.entry("RR", "Roraima"), Map.entry("SC", "Santa Catarina"),
        Map.entry("SP", "São Paulo"), Map.entry("SE", "Sergipe"), Map.entry("TO", "Tocantins"));

    private record Query(String operation, Map<?, ?> criteria) {}

    public String formatar(List<Map<String, Object>> evidence) {
        var groups = new LinkedHashMap<Query, List<Map<String, Object>>>();
        for (var item : evidence) {
            if (!(item.get("operacao") instanceof String operation)) continue;
            Map<?, ?> criteria = map(item.get("consulta"));
            if (criteria.isEmpty() && item.get("resultado") instanceof Map<?, ?> result)
                criteria = map(result.get("filtros"));
            groups.computeIfAbsent(new Query(operation, criteria), ignored -> new ArrayList<>()).add(item);
        }
        var paragraphs = new ArrayList<String>();
        for (var group : groups.entrySet()) {
            String text = formatGroup(group.getKey(), group.getValue());
            if (!text.isBlank()) paragraphs.add(text);
        }
        if (paragraphs.isEmpty()) return "Não foi possível apresentar os resultados consultados com segurança.";
        return "De acordo com o banco de dados do DataTerra:\n\n" + String.join("\n\n", paragraphs)
            + "\n\nOs dados representam os registros cadastrados no DataTerra, não um censo de todas as instalações existentes no Brasil.";
    }

    private String formatGroup(Query query, List<Map<String, Object>> pages) {
        Map<?, ?> latest = map(pages.getLast().get("resultado"));
        String context = context(query.criteria());
        if (query.operation().equals("COMPARAR")) {
            String text=state(latest.get("estadoA"))+": "+availableNumber(latest.get("totalA"))+" cadastrados; "
                +state(latest.get("estadoB"))+": "+availableNumber(latest.get("totalB"))+" cadastrados. Diferença absoluta: "
                +availableNumber(latest.get("diferencaAbsoluta"))+" data centers.";
            if(latest.get("percentual") instanceof Number) text+=" Percentual da diferença em relação a "+state(latest.get("denominador"))+": "+number(latest.get("percentual"))+"%.";
            if(latest.get("limitacao") instanceof String limitation) text+=" "+limitation;
            return text+" Base: "+string(latest.get("baseTemporal"))+". Datas das consultas: "+string(latest.get("consultadoEmA"))+" e "+string(latest.get("consultadoEmB"))+".";
        }
        if (query.operation().equals("CONTAR")) {
            String total = number(latest.get("total"));
            return total.isEmpty() ? "Contagem não disponível." : total + " data centers cadastrados" + context + ".";
        }
        if (query.operation().equals("MUNICIPIOS")) {
            String total = number(latest.get("totalMunicipios"));
            if(latest.get("registros") instanceof List<?> records) {
                var lines=new ArrayList<String>();
                for(Object record:records) {
                    var r=row(record);
                    lines.add("- "+field(r,"municipio")+" / "+field(r,"estado")+": "+availableNumber(r.get("quantidade"))+" data centers.");
                }
                return records.size()+" municípios retornados de "+total+context+".\n"+String.join("\n",lines)
                    +(Boolean.TRUE.equals(latest.get("completo"))?"":"\nListagem parcial.")
                    +(latest.get("alternativa") instanceof String s?"\n"+s:"");
            }
            return total.isEmpty() ? "Contagem de municípios não disponível." : total + " municípios possuem data centers cadastrados.";
        }
        // Unique keys retain the latest observed value; never sum repeated aggregate rows.
        var unique = new LinkedHashMap<Object, Map<String, Object>>();
        for (var page : pages) {
            for (Object item : rows(page)) {
                var row = row(item);
                if (row.isEmpty()) continue;
                Object key = switch (query.operation()) {
                    case "ESTADOS" -> stateCode(row.get("estado"));
                    case "OPERADORES" -> string(row.get("empresa"));
                    default -> row.get("id");
                };
                if (key == null || key.equals("")) key = row;
                unique.put(key, row);
            }
        }
        if (unique.isEmpty()) return "Nenhum registro encontrado para " + title(query.operation()).toLowerCase(PT_BR) + context
            + ". A ausência de registros nesta consulta não comprova ausência no mundo real."
            + (complete(pages, 0) ? "" : " Os resultados disponíveis são parciais.");
        var records = new ArrayList<>(unique.values());
        if (Set.of("ESTADOS", "OPERADORES").contains(query.operation()))
            records.sort(Comparator.<Map<String, Object>, BigDecimal>comparing(r -> decimal(r.get("quantidade"))).reversed()
                .thenComparing(r -> string(r.get(query.operation().equals("ESTADOS") ? "estado" : "empresa"))));
        var lines = new ArrayList<String>();
        for (var record : records) {
            String line = line(query.operation(), record);
            if (!line.isBlank()) lines.add("- " + line);
        }
        if (lines.isEmpty()) return "Não há informações suficientes para apresentar " + title(query.operation()).toLowerCase(PT_BR) + context + ".";
        return title(query.operation()) + context + ":\n" + String.join("\n", lines)
            + (complete(pages, unique.size()) ? "" : "\nResultados parciais: apenas os registros das páginas consultadas foram reunidos.");
    }

    private static String line(String operation, Map<String, Object> r) {
        return switch (operation) {
            case "ESTADOS" -> state(r.get("estado")) + ": " + availableNumber(r.get("quantidade")) + " data centers cadastrados.";
            case "OPERADORES" -> field(r, "empresa") + ": " + availableNumber(r.get("quantidade")) + " data centers cadastrados.";
            case "LISTAR" -> field(r, "nome") + optional(r, "empresa", " — ") + optional(r, "cidade", ", ")
                + (string(r.get("estado")).isEmpty() ? "" : ", " + state(r.get("estado")))
                + optional(r, "descricao", ". ") + optional(r, "potenciaMw", ". Potência cadastrada (MW): ")
                + optional(r, "areaM2", ". Área cadastrada (m²): ") + ".";
            case "INDICADORES" -> field(r, "tipo") + " — instalação " + field(r, "dataCenterId")
                + ": " + availableNumber(r.get("valor")) + optional(r, "unidade", " ")
                + optional(r, "periodo", "; período: ") + optional(r, "observacoes", "; observações: ")
                + optional(r, "fonteId", "; fonte cadastrada: ") + ".";
            case "BENEFICIOS" -> field(r, "tipo") + ": " + field(r, "descricao")
                + optional(r, "dataCenterId", "; instalação: ") + optional(r, "fonteId", "; fonte cadastrada: ") + ".";
            case "PESQUISAS" -> field(r, "titulo") + ": " + field(r, "conteudo")
                + optional(r, "categoria", "; categoria: ") + optional(r, "fonteId", "; fonte cadastrada: ") + ".";
            case "FONTES" -> field(r, "titulo") + optional(r, "instituicao", " — ") + optional(r, "url", ". ")
                + optional(r, "dataPublicacao", "; publicação: ") + ".";
            case "NOTICIAS" -> field(r, "titulo") + optional(r, "descricao", " — ") + optional(r, "fonte", "; fonte: ")
                + optional(r, "data_publicacao", "; publicação: ") + optional(r, "url", ". ") + ".";
            default -> "";
        };
    }

    /** Coverage uses offsets, so overlapping pages or a changed limit cannot imply completeness. */
    private static boolean complete(List<Map<String, Object>> pages, int uniqueCount) {
        var intervals = new TreeMap<Long, Long>();
        long total = -1;
        long end = Long.MAX_VALUE;
        for (var p : pages) {
            if (!(p.get("pagina") instanceof Number page) || !(p.get("limite") instanceof Number size)) continue;
            long offset = page.longValue() * size.longValue();
            int count = rows(p).size();
            intervals.merge(offset, offset + count, Math::max);
            Map<?, ?> result = map(p.get("resultado"));
            if (result.get("total") instanceof Number n) total = Math.max(total, n.longValue());
            if (count < size.longValue() || Boolean.FALSE.equals(result.get("temProximaPagina")))
                end = Math.min(end, offset + count);
        }
        long covered = 0;
        for (var interval : intervals.entrySet()) {
            if (interval.getKey() > covered) break;
            covered = Math.max(covered, interval.getValue());
        }
        return total >= 0 ? covered >= total && uniqueCount >= total : end != Long.MAX_VALUE && covered >= end;
    }

    private static List<?> rows(Map<String, Object> page) {
        Object result = page.get("resultado");
        if (result instanceof List<?> list) return list;
        Object records = map(result).get("registros");
        return records instanceof List<?> list ? list : List.of();
    }
    private static Map<?, ?> map(Object value) { return value instanceof Map<?, ?> m ? m : Map.of(); }
    private static Map<String, Object> row(Object value) {
        if (value instanceof Map<?, ?> m) {
            var result = new LinkedHashMap<String, Object>();
            m.forEach((k, v) -> { if (k instanceof String s) { result.put(s, v); result.putIfAbsent(s.toLowerCase(Locale.ROOT), v); } });
            return result;
        }
        return switch (value) {
            case data_centers.brasil.agent.dto.MunicipioCount m -> fields("municipio",m.municipio(),"estado",m.estado(),"quantidade",m.quantidade());
            case DataCenterResponse d -> fields("id",d.id(),"nome",d.nome(),"empresa",d.empresa(),"cidade",d.cidade(),"estado",d.estado(),"descricao",d.descricao(),"potenciaMw",d.potenciaMw(),"areaM2",d.areaM2());
            case IndicadorAmbientalResponse i -> fields("id",i.id(),"dataCenterId",i.dataCenterId(),"tipo",i.tipo(),"valor",i.valor(),"unidade",i.unidade(),"periodo",i.periodo(),"observacoes",i.observacoes(),"fonteId",i.fonteId());
            case BeneficioResponse b -> fields("id",b.id(),"dataCenterId",b.dataCenterId(),"tipo",b.tipo(),"descricao",b.descricao(),"fonteId",b.fonteId());
            case ConteudoPesquisaResponse c -> fields("id",c.id(),"titulo",c.titulo(),"conteudo",c.conteudo(),"categoria",c.categoria(),"fonteId",c.fonteId());
            case FonteResponse f -> fields("id",f.id(),"titulo",f.titulo(),"instituicao",f.instituicao(),"url",f.url(),"dataPublicacao",f.dataPublicacao());
            case null, default -> Map.of();
        };
    }
    private static Map<String, Object> fields(Object... entries) {
        var result = new LinkedHashMap<String, Object>();
        for (int i=0; i<entries.length; i+=2) result.put((String)entries[i], entries[i+1]);
        return result;
    }
    private static String context(Map<?, ?> filters) {
        var labels = Map.of("estado", "estado", "cidade", "município", "empresa", "operador", "nome", "instalação", "dataCenterId", "instalação", "fonteId", "fonte", "termo", "termo");
        var parts = new ArrayList<String>();
        for (String key : List.of("estado", "cidade", "empresa", "nome", "dataCenterId", "fonteId", "termo")) {
            Object value = filters.get(key);
            if (value == null) value = filters.get(key + "Eq");
            if (value instanceof List<?> list) value = list.isEmpty() ? null : list.getFirst();
            String text = key.equals("estado") ? state(value) : string(value);
            if (!text.isEmpty()) parts.add(labels.get(key) + ": " + text);
        }
        return parts.isEmpty() ? "" : " (" + String.join("; ", parts) + ")";
    }
    private static String title(String op) {
        return switch (op) {
            case "ESTADOS" -> "Distribuição de data centers por estado";
            case "OPERADORES" -> "Operadores cadastrados";
            case "LISTAR" -> "Data centers cadastrados";
            case "INDICADORES" -> "Indicadores ambientais cadastrados";
            case "BENEFICIOS" -> "Benefícios documentados";
            case "PESQUISAS" -> "Pesquisas cadastradas";
            case "NOTICIAS" -> "Notícias cadastradas";
            case "FONTES" -> "Fontes cadastradas";
            default -> "Consulta";
        };
    }
    private static String stateCode(Object value) { return string(value).trim().toUpperCase(Locale.ROOT); }
    private static String state(Object value) { String code = stateCode(value); return ESTADOS.getOrDefault(code, string(value)); }
    private static String field(Map<String, Object> r, String key) { String value=string(r.get(key)); return value.isEmpty() ? "não informado" : value; }
    private static String optional(Map<String, Object> r, String key, String prefix) {
        String value = r.get(key) instanceof Number ? number(r.get(key)) : string(r.get(key));
        return value.isEmpty() ? "" : prefix + value;
    }
    private static String availableNumber(Object value) { String n = number(value); return n.isEmpty() ? "não informado" : n; }
    private static BigDecimal decimal(Object value) { return value instanceof Number n ? new BigDecimal(n.toString()) : BigDecimal.ZERO; }
    private static String number(Object value) {
        if (!(value instanceof Number)) return "";
        var format = NumberFormat.getNumberInstance(PT_BR);
        format.setMaximumFractionDigits(10);
        return format.format(value);
    }
    /** Explicit scalars only: arbitrary Java objects must never leak into response text. */
    private static String string(Object value) {
        if (value instanceof String s) return s;
        if (value instanceof Number) return number(value);
        if (value instanceof java.time.temporal.TemporalAccessor || value instanceof java.util.Date) return value.toString();
        return "";
    }
}
