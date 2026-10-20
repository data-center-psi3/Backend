package data_centers.brasil.mapper;
import data_centers.brasil.dto.BeneficioResponse;
import data_centers.brasil.model.Beneficio;
public final class BeneficioMapper {
    private BeneficioMapper() {}
    public static BeneficioResponse toResponse(Beneficio entity) {
        return new BeneficioResponse(entity.getId(), entity.getDataCenter() == null ? null : entity.getDataCenter().getId(), entity.getTipo(), entity.getDescricao(), entity.getFonte() == null ? null : entity.getFonte().getId());
    }
}
