package data_centers.brasil.agent.service;

import data_centers.brasil.agent.visualization.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ChartTypeSelectionTests {
    final AgentVisualizationService service=new AgentVisualizationService(new AgentDatasetService());
    final List<Map<String,Object>> evidence=List.of(Map.of("operacao","TEST","resultado",Map.of("registros",List.of(
        Map.of("estado","SP","quantidade",4,"periodo","2024-01-01","area",10,"potencia",2),
        Map.of("estado","RJ","quantidade",2,"periodo","2025-01-01","area",20,"potencia",3)),"totalElements",2,"completo",true,"hasNext",false)));
    @ParameterizedTest
    @CsvSource(delimiter='|',value={
        "Gráfico de pizza|pie", "Pie chart|pie", "Gráfico pie|pie", "Em gráfico de pie.|pie", "Em formato de pie|pie",
        "Gráfico de rosca|donut", "Donut chart|donut", "Doughnut chart|donut", "Gráfico de barras|bar",
        "Horizontal bar chart|horizontalBar", "Em barras horizontais|horizontalBar", "Gráfico de linhas|line", "Line chart|line",
        "Gráfico de dispersão|scatter", "Scatter plot|scatter"})
    void aliasesOverrideConflictingGeminiPlanWithoutHistory(String question,String expected) {
        var intent=VisualizationIntent.from(question);
        assertTrue(intent.requested());assertTrue(intent.conversion());assertEquals(expected,intent.chartType());
        var plan=new LinkedHashMap<String,Object>();plan.put("chartType","horizontalBar");
        if(expected.equals("scatter")) { plan.put("x","area");plan.put("y","potencia"); }
        var spec=service.build(evidence,plan,intent);
        assertEquals(expected,spec.get("chartType"));
    }
    @Test void currentGeminiSelectionPrecedesAutomaticSelection() {
        var intent=VisualizationIntent.from("Visualize os dados.");
        assertEquals("pie",service.build(evidence,Map.of("chartType","pie"),intent).get("chartType"));
        assertEquals("bar",service.build(evidence,Map.of(),intent).get("chartType"));
    }
    @Test void legacyDonutIdentifierIsNormalized() {
        assertEquals("donut",service.build(evidence,Map.of("chartType","doughnut"),VisualizationIntent.from("Gráfico")).get("chartType"));
    }
    @Test void lastRequestedRepresentationWinsAndNewFiltersAreNotConversions() {
        assertEquals("bar",VisualizationIntent.from("Transforme pizza em barras").chartType());
        assertEquals("chart",VisualizationIntent.from("Transforme a tabela em gráfico pie").type());
        assertEquals("table",VisualizationIntent.from("Transforme o pie chart em tabela").type());
        assertFalse(VisualizationIntent.from("Atualize os dados de SP em gráfico de pizza").conversion());
        assertFalse(VisualizationIntent.from("Mostre os dados por UF em gráfico de pizza").conversion());
        assertNull(VisualizationIntent.from("Copiei o relatório").chartType());
    }
    @Test void genuinelyIncompatiblePieExplainsTheAlternative() {
        var invalid=List.of(Map.<String,Object>of("operacao","TEST","resultado",Map.of("registros",List.of(Map.of("estado","SP","quantidade",-1)),"completo",true,"hasNext",false,"totalElements",1)));
        var spec=service.fallback(invalid,VisualizationIntent.from("Em gráfico de pie"),"");
        assertEquals("table",spec.get("type"));var meta=(Map<?,?>)spec.get("metadata");
        assertEquals("unsupported",meta.get("status"));assertTrue(meta.get("message").toString().contains("não negativos"));
    }
}
