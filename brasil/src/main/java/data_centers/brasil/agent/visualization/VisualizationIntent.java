package data_centers.brasil.agent.visualization;

import data_centers.brasil.filter.TextNormalization;
import java.util.*;

/** Intent hints for deterministic fallback; Gemini's validated proposal remains the planner. */
public record VisualizationIntent(boolean requested,boolean conversion,String type,String chartType,boolean percentage) {
    public static VisualizationIntent from(String question) {
        String q=TextNormalization.normalize(question);
        String kind=ChartType.explicit(q);
        boolean table=q.matches("(?s).*(tabela|em colunas|tabular|\\btable\\b).*"), chart=kind!=null || q.matches("(?s).*(grafico|visualiz|\\bchart\\b|\\bplot\\b).*");
        if(table && chart) {
            int tablePosition=Math.max(q.lastIndexOf("tabela"),q.lastIndexOf("colunas"));
            int chartPosition=List.of("grafico","visualiz","pizza","pie","rosca","donut","doughnut","dispersao","scatter","barras","bar","line","linha","chart","plot").stream().mapToInt(q::lastIndexOf).max().orElse(-1);
            table=tablePosition>chartPosition;
        }
        boolean convert=(table||chart) && (q.matches("(?s).*(agora|transform|convert|volte|mesmos|mesmas|esses dados|estes dados|resultados anteriores).*") || ChartType.formatOnly(q));
        if(q.matches("(?s).*(atualiz|refaca|reconsult|nova consulta|filtr|somente|apenas).*")) convert=false;
        return new VisualizationIntent(table||chart,convert,table?"table":"chart",kind,q.contains("percent") || q.contains("participacao"));
    }
    public boolean all(String question) {
        return TextNormalization.normalize(question).matches("(?s).*(\\btod[oa]s\\b|completa|sem omitir|98 municipios|cada um).*");
    }
}
