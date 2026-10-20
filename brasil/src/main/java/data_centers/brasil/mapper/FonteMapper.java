package data_centers.brasil.mapper;
import data_centers.brasil.dto.FonteResponse;
import data_centers.brasil.model.Fonte;
public final class FonteMapper {
    private FonteMapper() {}
    public static FonteResponse toResponse(Fonte entity) {
        return new FonteResponse(entity.getId(), entity.getInstituicao(), entity.getTitulo(), entity.getUrl(), entity.getDataPublicacao(), entity.getDataConsulta());
    }
}
