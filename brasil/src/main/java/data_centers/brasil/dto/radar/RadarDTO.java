package data_centers.brasil.dto.radar;

import java.math.BigDecimal;
import java.util.List;

/** Contratos imutáveis do Radar; números ausentes permanecem null. */
public final class RadarDTO {
    private RadarDTO() {}
    public record Metrics(long totalInstalacoes, long totalOperadores, long totalMunicipios,
        long totalEstados, long instalacoesComCoordenadas,
        BigDecimal totalRedesRegistradas, BigDecimal mediaRedesPorInstalacao,
        BigDecimal totalIxRegistrados, BigDecimal mediaIxPorInstalacao,
        BigDecimal totalCarriersRegistrados, BigDecimal mediaCarriersPorInstalacao,
        long instalacoesComConectividade, long instalacoesComRedes, long instalacoesComIx,
        long instalacoesComCarriers) {}
    public record State(String uf, String nome, long totalInstalacoes, long totalOperadores,
        long totalMunicipios, BigDecimal participacaoNacional, BigDecimal totalRedesRegistradas,
        BigDecimal mediaRedesPorInstalacao, BigDecimal totalIxRegistrados, BigDecimal mediaIxPorInstalacao,
        BigDecimal totalCarriersRegistrados, BigDecimal mediaCarriersPorInstalacao,
        long instalacoesComCoordenadas, long instalacoesComConectividade,
        long instalacoesComRedes, long instalacoesComIx, long instalacoesComCarriers) {}
    public record StateDetail(String uf, String nome, long totalInstalacoes, long totalOperadores,
        long totalMunicipios, BigDecimal participacaoNacional, BigDecimal totalRedesRegistradas,
        BigDecimal mediaRedesPorInstalacao, BigDecimal totalIxRegistrados, BigDecimal mediaIxPorInstalacao,
        BigDecimal totalCarriersRegistrados, BigDecimal mediaCarriersPorInstalacao,
        long instalacoesComCoordenadas, long instalacoesComConectividade,
        long instalacoesComRedes, long instalacoesComIx, long instalacoesComCarriers,
        List<Operator> operadores, List<City> municipios) {}
    public record City(String municipio, String uf, String codigoIbge, long totalInstalacoes,
        long totalOperadores, BigDecimal totalRedesRegistradas, BigDecimal mediaRedesPorInstalacao,
        BigDecimal totalIxRegistrados, BigDecimal totalCarriersRegistrados) {}
    public record Operator(String operador, long totalInstalacoes, long totalEstados,
        long totalMunicipios, BigDecimal totalRedesRegistradas) {}
    // A tabela bruta não possui PK. id é o id_original textual, sem unicidade garantida.
    public record MapPoint(String id, String operador, String unidade, String municipio, String uf,
        BigDecimal latitude, BigDecimal longitude, String precisaoGeo, String tipoInstalacao,
        BigDecimal redesConectadas, BigDecimal ixConectados, BigDecimal carriers) {}
    public record Filters(List<String> states, List<String> operators,
        List<String> installationTypes, List<String> regions) {}
}
