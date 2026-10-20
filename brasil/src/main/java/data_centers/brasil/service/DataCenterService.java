package data_centers.brasil.service;
import data_centers.brasil.filter.*;
import org.springframework.util.MultiValueMap;
import data_centers.brasil.dto.*;
import data_centers.brasil.model.*;
import data_centers.brasil.repository.*;
import data_centers.brasil.mapper.DataCenterMapper;
import data_centers.brasil.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class DataCenterService {
    private final DataCenterRepository repository;
    private final QueryFilterParser filterParser;
    private final IndicadorAmbientalRepository indicadores;
    private final BeneficioRepository beneficios;
    private final ConteudoPesquisaRepository conteudos;
    private final DataCenterFonteRepository vinculos;

    public Page<DataCenterResponse> list(MultiValueMap<String, String> filters, Pageable pageable) {
        return repository.findAll(JpaFilters.<DataCenter>specification(filterParser.parse(filters, FilterResource.DATA_CENTER)), pageable).map(DataCenterMapper::toResponse);
    }
    public DataCenterResponse get(Integer id) { return DataCenterMapper.toResponse(find(id)); }
    private DataCenter find(Integer id) { return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("DataCenter", id)); }
    @Transactional
    public DataCenterResponse create(DataCenterRequest request) { return save(new DataCenter(), request); }
    @Transactional
    public DataCenterResponse update(Integer id, DataCenterRequest request) { return save(find(id), request); }
    private DataCenterResponse save(DataCenter entity, DataCenterRequest request) {
        entity.setNome(request.nome());
        entity.setEmpresa(request.empresa());
        entity.setCidade(request.cidade());
        entity.setEstado(request.estado());
        entity.setPais(request.pais() == null ? "Brasil" : request.pais());
        entity.setLatitude(request.latitude());
        entity.setLongitude(request.longitude());
        entity.setDescricao(request.descricao());
        entity.setPotenciaMw(request.potenciaMw());
        entity.setAreaM2(request.areaM2());
        entity.setTipoInstalacao(request.tipoInstalacao());
        entity.setStatusValidacao(request.statusValidacao());
        entity.setDataValidacao(request.dataValidacao());
        return DataCenterMapper.toResponse(repository.saveAndFlush(entity));
    }
    @Transactional
    public void delete(Integer id) {
        var entity = find(id);
        if (indicadores.existsByDataCenterId(id) || beneficios.existsByDataCenterId(id)
            || conteudos.existsByDataCenterId(id) || vinculos.existsByIdDataCenterId(id)) {
            throw new data_centers.brasil.exception.ResourceConflictException("Data center possui registros vinculados. Remova os vínculos antes de excluir.");
        }
        repository.delete(entity);
        repository.flush();
    }
}
