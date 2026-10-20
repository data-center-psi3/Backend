package data_centers.brasil.service;
import data_centers.brasil.filter.*;
import org.springframework.util.MultiValueMap;
import data_centers.brasil.dto.*;
import data_centers.brasil.model.*;
import data_centers.brasil.repository.*;
import data_centers.brasil.mapper.IndicadorAmbientalMapper;
import data_centers.brasil.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class IndicadorAmbientalService {
    private final IndicadorAmbientalRepository repository;
    private final QueryFilterParser filterParser;
    private final DataCenterRepository dataCenterRepository;
    private final FonteRepository fonteRepository;
    public Page<IndicadorAmbientalResponse> list(MultiValueMap<String, String> filters, Pageable pageable) {
        return repository.findAll(JpaFilters.<IndicadorAmbiental>specification(filterParser.parse(filters, FilterResource.INDICADOR_AMBIENTAL)), pageable).map(IndicadorAmbientalMapper::toResponse);
    }
    public IndicadorAmbientalResponse get(Integer id) { return IndicadorAmbientalMapper.toResponse(find(id)); }
    private IndicadorAmbiental find(Integer id) { return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("IndicadorAmbiental", id)); }
    @Transactional
    public IndicadorAmbientalResponse create(IndicadorAmbientalRequest request) { return save(new IndicadorAmbiental(), request); }
    @Transactional
    public IndicadorAmbientalResponse update(Integer id, IndicadorAmbientalRequest request) { return save(find(id), request); }
    private IndicadorAmbientalResponse save(IndicadorAmbiental entity, IndicadorAmbientalRequest request) {
        entity.setDataCenter(request.dataCenterId() == null ? null : dataCenterRepository.findById(request.dataCenterId()).orElseThrow(() -> new ResourceNotFoundException("DataCenter", request.dataCenterId())));
        entity.setTipo(request.tipo());
        entity.setValor(request.valor());
        entity.setUnidade(request.unidade());
        entity.setPeriodo(request.periodo());
        entity.setFonte(request.fonteId() == null ? null : fonteRepository.findById(request.fonteId()).orElseThrow(() -> new ResourceNotFoundException("Fonte", request.fonteId())));
        entity.setObservacoes(request.observacoes());
        return IndicadorAmbientalMapper.toResponse(repository.saveAndFlush(entity));
    }
    @Transactional
    public void delete(Integer id) { repository.delete(find(id)); repository.flush(); }
}
