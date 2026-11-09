package data_centers.brasil.agent.service;

import data_centers.brasil.agent.visualization.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AgentVisualizationTests {
    AgentDatasetService datasets=new AgentDatasetService();
    AgentVisualizationService service=new AgentVisualizationService(datasets);
    Map<String,Object> evidence(List<?> rows) {
        return Map.of("operacao","TEST","consulta",Map.of("estado","SP"),"pagina",0,"limite",20,
            "resultado",Map.of("registros",rows,"totalElements",rows.size(),"completo",true,"hasNext",false));
    }
    List<Map<String,Object>> states() { return List.of(Map.of("estado","SP","quantidade",4),Map.of("estado","RJ","quantidade",2)); }
    Map<String,Object> build(List<?> rows,String question,Map<?,?> plan) { return service.build(List.of(evidence(rows)),plan,VisualizationIntent.from(question)); }
    Map<?,?> metadata(Map<?,?> s) { return (Map<?,?>)s.get("metadata"); }
    @Test void barByUfBindsRealValuesAndPreservesFilters() {
        var spec=build(states(),"Faça um gráfico por UF",Map.of("type","chart","x","estado","y","quantidade"));
        assertEquals("bar",spec.get("chartType")); assertEquals(states(),spec.get("data"));
        assertTrue(metadata(spec).get("queries").toString().contains("SP")); assertEquals(true,metadata(spec).get("complete"));
    }
    @Test void pieUsesRealTotalForPercentageAndNeverReplacesCounts() {
        var spec=build(states(),"Gráfico de pizza de participação percentual",Map.of());
        assertEquals("pie",spec.get("chartType"));assertEquals("6",metadata(spec).get("percentageDenominator").toString());
        assertEquals(states(),spec.get("data"));
    }
    @Test void horizontalChartKeepsNinetyEightCategories() {
        var rows=new ArrayList<Map<String,Object>>();for(int i=0;i<98;i++) rows.add(Map.of("municipio","M"+i,"estado","SP","quantidade",i+1));
        var spec=build(rows,"Gráfico de todos os municípios",Map.of());
        assertEquals("horizontalBar",spec.get("chartType"));assertEquals(rows,spec.get("data"));
    }
    @Test void temporalLineUsesActualPeriods() {
        var rows=List.of(Map.of("periodo","2024-01-01","valor",3),Map.of("periodo","2025-01-01","valor",5));
        assertEquals("line",build(rows,"Mostre em gráfico de linhas",Map.of()).get("chartType"));
    }
    @Test void invalidTemporalChartReturnsTableAndClearLimitation() {
        assertThrows(IllegalArgumentException.class,()->build(states(),"Gráfico de linhas",Map.of()));
        var spec=service.fallback(List.of(evidence(states())),VisualizationIntent.from("Gráfico de linhas"),"");
        assertEquals("table",spec.get("type"));assertEquals("unsupported",metadata(spec).get("status"));
    }
    @Test void scatterRequiresTwoNumericAxes() {
        var rows=List.of(Map.of("potencia",10,"area",100),Map.of("potencia",20,"area",200));
        assertEquals("scatter",build(rows,"Gráfico de dispersão",Map.of("x","area","y","potencia")).get("chartType"));
        assertThrows(IllegalArgumentException.class,()->build(states(),"Gráfico de dispersão",Map.of("x","estado","y","quantidade")));
    }
    @Test void categoricalSeriesAndMultipleMetrics() {
        var rows=List.of(Map.of("periodo","2024","valor",2,"tipo","A"),Map.of("periodo","2024","valor",3,"tipo","B"));
        var spec=build(rows,"Gráfico de linhas",Map.of("series","tipo","x","periodo","y","valor"));
        assertTrue(((Map<?,?>)spec.get("encoding")).containsKey("color"));
        assertEquals(List.of("a","b"),((Map<?,?>)((Map<?,?>)build(List.of(Map.of("nome","x","a",1,"b",2)),"Gráfico",Map.of("yFields",List.of("a","b"))).get("encoding")).get("y")).get("fields"));
    }
    @Test void tableProvidesTypedColumnsAndOriginalRows() {
        var spec=build(states(),"Organize em colunas",Map.of());
        assertEquals("table",spec.get("type"));assertEquals(states(),spec.get("rows"));assertEquals(2,((List<?>)spec.get("columns")).size());
    }
    @Test void rejectsUnknownFieldOrInventedDataset() {
        assertThrows(IllegalArgumentException.class,()->build(states(),"Gráfico",Map.of("y","inventado")));
        assertThrows(IllegalArgumentException.class,()->build(states(),"Gráfico",Map.of("datasetId",UUID.randomUUID().toString())));
    }
    @Test void rejectsExecutablePropertiesAndUnsafeTypes() {
        for(var plan:List.of(Map.of("javascript","alert(1)"),Map.of("sql","select 1"),Map.of("chartType","script"),Map.of("title","<script>"),Map.of("format","eval")))
            assertThrows(IllegalArgumentException.class,()->build(states(),"Gráfico",plan));
    }
    @Test void nullsRemainNullAndNeverBecomeZero() {
        var row=new LinkedHashMap<String,Object>();row.put("estado","MG");row.put("quantidade",null);
        var spec=build(List.of(states().getFirst(),row),"Gráfico",Map.of());
        assertNull(((Map<?,?>)((List<?>)spec.get("data")).getLast()).get("quantidade"));
    }
    @Test void allNullMetricIsRejectedWithoutInventingNumbers() {
        var row=new HashMap<String,Object>();row.put("estado","SP");row.put("quantidade",null);
        assertThrows(IllegalArgumentException.class,()->build(List.of(row),"Gráfico",Map.of("y","quantidade")));
    }
    @Test void multipleToolsRemainSeparateAndSelectable() {
        var a=evidence(states());var b=new LinkedHashMap<>(evidence(List.of(Map.of("empresa","A","quantidade",7))));b.put("operacao","OPERADORES");
        var available=datasets.datasets(List.of(a,b));assertEquals(2,available.size());
        var spec=service.build(List.of(a,b),Map.of("datasetId",available.getFirst().id()),VisualizationIntent.from("Gráfico"));
        assertEquals(states(),spec.get("data"));assertEquals(2,((List<?>)metadata(spec).get("availableDatasets")).size());
    }
    @Test void explicitTypeOverridesModelAndTooManyPieCategoriesAreNotTruncated() {
        assertEquals("pie",build(states(),"Gráfico de pizza",Map.of("chartType","bar")).get("chartType"));
        var rows=new ArrayList<Map<String,Object>>();for(int i=0;i<98;i++) rows.add(Map.of("municipio","M"+i,"quantidade",1));
        var spec=service.fallback(List.of(evidence(rows)),VisualizationIntent.from("Gráfico de pizza"),"");
        assertEquals("pie",spec.get("chartType"));assertEquals("ok",metadata(spec).get("status"));assertTrue(metadata(spec).containsKey("warning"));assertEquals(98,((List<?>)spec.get("data")).size());
    }
    @Test void emptyDataReturnsARealEmptyTable() {
        var spec=service.fallback(List.of(evidence(List.of())),VisualizationIntent.from("Gráfico"),"");
        assertEquals(List.of(),spec.get("data"));assertEquals(0L,metadata(spec).get("totalRecords"));
    }
    @Test void pagesMergeInSourceOrderAndMissingPageIsIncomplete() {
        var a=new LinkedHashMap<>(evidence(List.of(Map.of("nome","A","quantidade",1))));a.put("limite",1);a.put("resultado",Map.of("registros",List.of(Map.of("nome","A","quantidade",1)),"totalElements",3));
        var b=new LinkedHashMap<>(a);b.put("pagina",2);b.put("resultado",Map.of("registros",List.of(Map.of("nome","C","quantidade",1)),"totalElements",3,"hasNext",false));
        var d=datasets.datasets(List.of(a,b)).getFirst();assertFalse(d.complete());assertEquals(2,d.data().size());
    }
    @Test void seriesLimitAndNegativePieValuesAreRejected() {
        var rows=new ArrayList<Map<String,Object>>();for(int i=0;i<21;i++) rows.add(Map.of("estado","SP","tipo","T"+i,"quantidade",1));
        assertThrows(IllegalArgumentException.class,()->build(rows,"Gráfico",Map.of("series","tipo")));
        assertThrows(IllegalArgumentException.class,()->build(List.of(Map.of("estado","SP","quantidade",-1)),"Gráfico de pizza",Map.of()));
    }
    @Test void responseLimitReturnsReferenceWithoutSilentlySampling() {
        var rows=new ArrayList<Map<String,Object>>();for(int i=0;i<2001;i++) rows.add(Map.of("nome","N"+i,"quantidade",1));
        var spec=build(rows,"Gráfico",Map.of());assertEquals("limit_exceeded",metadata(spec).get("status"));assertEquals(List.of(),spec.get("data"));assertTrue(metadata(spec).containsKey("dataEndpoint"));
    }
    @Test void validatesSortGroupingAndHonorsExplicitBars() {
        var spec=build(states(),"Gráfico de barras",Map.of("chartType","pie","sortField","quantidade","sortDirection","descending","groupBy",List.of("estado")));
        assertEquals("bar",spec.get("chartType"));assertEquals(List.of("estado"),spec.get("groupBy"));
        assertThrows(IllegalArgumentException.class,()->build(states(),"Gráfico",Map.of("sortField","evil")));
        assertThrows(IllegalArgumentException.class,()->build(states(),"Gráfico",Map.of("groupBy",List.of("evil"))));
    }
    @Test void scalarCountUsesItsActualFilterAsDimension() {
        var e=Map.<String,Object>of("operacao","CONTAR","consulta",Map.of("estado","SP"),"resultado",Map.of("total",7));
        var spec=service.build(List.of(e),Map.of(),VisualizationIntent.from("Gráfico"));
        assertEquals("bar",spec.get("chartType"));assertEquals(List.of(Map.of("total",7,"estado","SP")),spec.get("data"));assertEquals(1L,metadata(spec).get("totalRecords"));
    }
    @Test void mixedUnitsAndInventedUnitsAreRejected() {
        var rows=List.of(Map.of("tipo","A","valor",2,"unidade","MW"),Map.of("tipo","B","valor",3,"unidade","litros"));
        assertThrows(IllegalArgumentException.class,()->build(rows,"Gráfico",Map.of("y","valor")));
        assertThrows(IllegalArgumentException.class,()->build(states(),"Gráfico",Map.of("unit","MW")));
    }
}
