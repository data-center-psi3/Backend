package data_centers.brasil.agent.formatter;

import data_centers.brasil.dto.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AgentFallbackFormatterTests {
    final AgentFallbackFormatter formatter = new AgentFallbackFormatter();
    private Map<String,Object> page(String op,int index,int size,Object result) {
        return Map.of("operacao",op,"pagina",index,"limite",size,"resultado",result);
    }
    private Map<String,Object> state(String uf,long count) { return Map.of("estado",uf,"quantidade",count); }
    private String format(Map<String,Object> p) { return formatter.formatar(List.of(p)); }
    @Test void singlePageStateRankingUsesNamesAndSortsByActualCounts() {
        String text=format(page("ESTADOS",0,20,List.of(state("RS",32),state("SP",112),state("RJ",34))));
        assertTrue(text.contains("São Paulo: 112 data centers cadastrados."));
        assertTrue(text.indexOf("São Paulo") < text.indexOf("Rio de Janeiro"));
        assertTrue(text.indexOf("Rio de Janeiro") < text.indexOf("Rio Grande do Sul"));
        assertFalse(text.contains("parciais"));
        assertFalse(text.contains("operacao"));
        assertFalse(text.contains("resultado="));
        assertFalse(text.contains("Minas Gerais"));
    }
    @Test void repeatedAndOverlappingPagesNeverDuplicateOrSumAggregates() {
        var p0=page("ESTADOS",0,2,List.of(state("SP",112),state("RJ",34)));
        var p1=page("ESTADOS",1,2,List.of(state("RJ",34),state("RS",32)));
        var end=page("ESTADOS",2,2,List.of());
        String text=formatter.formatar(List.of(p0,p1,p0,end));
        assertEquals(1,text.split("São Paulo: 112",-1).length-1);
        assertEquals(1,text.split("Rio de Janeiro: 34",-1).length-1);
        assertFalse(text.contains("224"));
        assertFalse(text.contains("68 data centers"));
    }
    @Test void gapsAndFullPagesAreDisclosedAsPartial() {
        String text=formatter.formatar(List.of(page("ESTADOS",0,1,List.of(state("SP",112))),page("ESTADOS",2,1,List.of())));
        assertTrue(text.contains("Resultados parciais"));
        assertTrue(format(page("ESTADOS",0,1,List.of(state("SP",112)))).contains("Resultados parciais"));
    }
    @Test void changingPageSizeAndOverlappingRangesStillConsolidates() {
        String text=formatter.formatar(List.of(
            page("ESTADOS",0,2,List.of(state("SP",112),state("RJ",34))),
            page("ESTADOS",1,1,List.of(state("RJ",34))),
            page("ESTADOS",1,2,List.of(state("RS",32)))));
        assertFalse(text.contains("parciais"));
        assertEquals(1,text.split("Rio de Janeiro: 34",-1).length-1);
    }
    @Test void emptyResultsDoNotInventStatesOrTreatLaterPageAsEmptyDatabase() {
        String text=format(page("ESTADOS",0,20,List.of()));
        assertTrue(text.contains("Nenhum registro encontrado"));
        assertFalse(text.contains("São Paulo"));
        assertFalse(text.contains("parciais"));
        assertTrue(format(page("ESTADOS",2,20,List.of())).contains("parciais"));
    }
    @Test void countsAndComparisonsLabelEachFilterWithoutRenderingMaps() {
        var sp=page("CONTAR",0,20,Map.of("total",112,"filtros",Map.of("estadoEq",List.of("SP"))));
        var rj=page("CONTAR",0,20,Map.of("total",34,"filtros",Map.of("estadoEq",List.of("RJ"))));
        String text=formatter.formatar(List.of(sp,rj,sp));
        assertTrue(text.contains("112 data centers cadastrados (estado: São Paulo)"));
        assertTrue(text.contains("34 data centers cadastrados (estado: Rio de Janeiro)"));
        assertFalse(text.contains("estadoEq"));
        assertFalse(text.contains("{"));
        assertEquals(1,text.split("112 data centers",-1).length-1);
    }
    @Test void dtoListingsAreFormattedAndDeduplicatedById() {
        var dc=new DataCenterResponse(1,"Campus A","Operador A","Campinas","SP",null,null,null,null,null,null,null,null,null);
        var p0=page("LISTAR",0,1,Map.of("registros",List.of(dc),"total",1L,"temProximaPagina",false));
        String text=formatter.formatar(List.of(p0,p0));
        assertTrue(text.contains("Campus A — Operador A, Campinas, São Paulo"));
        assertEquals(1,text.split("Campus A",-1).length-1);
        assertFalse(text.contains("DataCenterResponse"));
        assertFalse(text.contains("parciais"));
    }
    @Test void differentQueriesForSameOperationRemainSeparate() {
        var p0=new HashMap<>(page("INDICADORES",0,20,Map.of("registros",List.of(),"total",0L,"temProximaPagina",false)));
        p0.put("consulta",Map.of("dataCenterId",1));
        var p1=new HashMap<>(p0); p1.put("consulta",Map.of("dataCenterId",2));
        String text=formatter.formatar(List.of(p0,p1));
        assertTrue(text.contains("instalação: 1")); assertTrue(text.contains("instalação: 2"));
    }
    @Test void indicatorsPreserveUnitsPeriodsSourcesAndMissingValues() {
        var indicator=new IndicadorAmbientalResponse(1,2,"PUE",new BigDecimal("1.25"),"índice","2026",3,"Declarado");
        String text=format(page("INDICADORES",0,20,Map.of("registros",List.of(indicator),"total",1L,"temProximaPagina",false)));
        assertTrue(text.contains("PUE — instalação 2: 1,25 índice"));
        assertTrue(text.contains("período: 2026")); assertTrue(text.contains("fonte cadastrada: 3"));
        var missing=new IndicadorAmbientalResponse(2,2,"WUE",null,null,null,null,null);
        assertTrue(format(page("INDICADORES",0,20,Map.of("registros",List.of(missing),"total",1L))).contains("não informado"));
    }
    @Test void remainingOperationsHaveExplicitHumanReadableFormats() {
        assertTrue(format(page("MUNICIPIOS",0,20,Map.of("totalMunicipios",7))).contains("7 municípios"));
        assertTrue(format(page("OPERADORES",0,20,List.of(Map.of("empresa","Empresa A","quantidade",3)))).contains("Empresa A: 3"));
        assertTrue(format(page("NOTICIAS",0,20,List.of(Map.of("id",1,"titulo","Notícia A","url","https://example.test")))).contains("Notícia A. https://example.test"));
        var benefit=new BeneficioResponse(1,2,"Empregos","Descrição A",3);
        assertTrue(format(page("BENEFICIOS",0,20,Map.of("registros",List.of(benefit)))).contains("Empregos: Descrição A"));
        var research=new ConteudoPesquisaResponse(1,2,"Pesquisa A","Conteúdo A","Categoria A",3,null);
        assertTrue(format(page("PESQUISAS",0,20,Map.of("registros",List.of(research)))).contains("Pesquisa A: Conteúdo A"));
        var source=new FonteResponse(1,"Instituição A","Relatório A","https://example.test",null,null);
        assertTrue(format(page("FONTES",0,20,Map.of("registros",List.of(source)))).contains("Relatório A — Instituição A. https://example.test"));
    }
    @Test void unknownObjectsAreNeverPrinted() {
        String text=format(page("LISTAR",0,20,Map.of("registros",List.of(new Object()))));
        assertFalse(text.contains("java.lang.Object"));
        assertFalse(text.contains("@"));
        assertTrue(text.contains("Nenhum registro"));
    }
}
