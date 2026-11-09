package data_centers.brasil.agent.visualization;

import data_centers.brasil.filter.TextNormalization;
import java.util.*;
import java.util.regex.*;

/** Shared vocabulary for explicit requests, model proposals, and persisted legacy plans. */
public enum ChartType {
    HORIZONTAL_BAR("horizontalBar", "barras? horizontais|barras? horizontal|horizontal(?: bar)?(?: chart)?|horizontalbar"),
    BAR("bar", "barras?|bar(?: chart)?"),
    PIE("pie", "pizza|pie(?: chart)?"),
    DONUT("donut", "rosca|donut(?: chart)?|doughnut(?: chart)?"),
    LINE("line", "linhas?|line(?: chart)?|evolucao"),
    AREA("area", "grafico de area|area chart"),
    SCATTER("scatter", "dispersao|scatter(?: plot)?");

    private final String id;
    private final Pattern expression;
    ChartType(String id,String aliases) {
        this.id=id;this.expression=Pattern.compile("\\b(?:"+aliases+")\\b");
    }
    public String id() { return id; }
    public static List<String> identifiers() { return Arrays.stream(values()).map(ChartType::id).toList(); }
    public static String normalize(String value) {
        String normalized=TextNormalization.normalize(value);
        for(var type:values()) if(type.id.toLowerCase(Locale.ROOT).equals(normalized) || type.expression.matcher(normalized).matches()) return type.id;
        throw new IllegalArgumentException("Tipo de gráfico não permitido.");
    }
    public static String explicit(String question) {
        String q=TextNormalization.normalize(question),chosen=null;int end=-1,length=-1;
        for(var type:values()) {
            var matches=type.expression.matcher(q);
            while(matches.find()) {
                int size=matches.end()-matches.start();
                if(matches.end()>end || matches.end()==end && size>length) {
                    chosen=type.id;end=matches.end();length=size;
                }
            }
        }
        return chosen;
    }
    public static boolean circular(String value) { return value!=null && Set.of("pie","donut").contains(normalize(value)); }
    public static boolean formatOnly(String question) {
        String rest=TextNormalization.normalize(question);
        for(var type:values()) rest=type.expression.matcher(rest).replaceAll(" ");
        rest=rest.replaceAll("\\b(?:em|de|do|da|o|a|um|uma|formato|grafico|graficos|chart|plot|tabela|table|colunas|agora|volte|para|mostre|apresente|organize|transforme|converta|por favor)\\b"," ");
        return rest.replaceAll("[\\s\\p{Punct}]+","").isEmpty();
    }
}
