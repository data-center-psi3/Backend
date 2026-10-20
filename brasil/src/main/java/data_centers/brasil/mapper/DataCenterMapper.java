package data_centers.brasil.mapper;
import data_centers.brasil.dto.DataCenterResponse;
import data_centers.brasil.model.DataCenter;
public final class DataCenterMapper {
    private DataCenterMapper() {}
    public static DataCenterResponse toResponse(DataCenter entity) {
        return new DataCenterResponse(entity.getId(), entity.getNome(), entity.getEmpresa(), entity.getCidade(), entity.getEstado(), entity.getPais(), entity.getLatitude(), entity.getLongitude(), entity.getDescricao(), entity.getPotenciaMw(), entity.getAreaM2(), entity.getTipoInstalacao(), entity.getStatusValidacao(), entity.getDataValidacao());
    }
}
