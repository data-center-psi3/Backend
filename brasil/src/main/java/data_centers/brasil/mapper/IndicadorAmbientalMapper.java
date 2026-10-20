package data_centers.brasil.mapper;
import data_centers.brasil.dto.IndicadorAmbientalResponse;
import data_centers.brasil.model.IndicadorAmbiental;
public final class IndicadorAmbientalMapper {
    private IndicadorAmbientalMapper() {}
    public static IndicadorAmbientalResponse toResponse(IndicadorAmbiental entity) {
        return new IndicadorAmbientalResponse(entity.getId(), entity.getDataCenter() == null ? null : entity.getDataCenter().getId(), entity.getTipo(), entity.getValor(), entity.getUnidade(), entity.getPeriodo(), entity.getFonte() == null ? null : entity.getFonte().getId(), entity.getObservacoes());
    }
}
