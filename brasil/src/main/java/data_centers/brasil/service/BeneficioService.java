package data_centers.brasil.service;
import data_centers.brasil.filter.*;
import org.springframework.util.MultiValueMap;
import data_centers.brasil.dto.*;
import data_centers.brasil.model.*;
import data_centers.brasil.repository.*;
import data_centers.brasil.mapper.BeneficioMapper;
import data_centers.brasil.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class BeneficioService {
    private final BeneficioRepository repository;
    private final QueryFilterParser filterParser;
    private final DataCenterRepository dataCenterRepository;
    private final FonteRepository fonteRepository;
    public Page<BeneficioResponse> list(MultiValueMap<String, String> filters, Pageable pageable) {
        return repository.findAll(JpaFilters.<Beneficio>specification(filterParser.parse(filters, FilterResource.BENEFICIO)), pageable).map(BeneficioMapper::toResponse);
    }
    public BeneficioResponse get(Integer id) { return BeneficioMapper.toResponse(find(id)); }
    private Beneficio find(Integer id) { return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Beneficio", id)); }
    @Transactional
    public BeneficioResponse create(BeneficioRequest request) { return save(new Beneficio(), request); }
    @Transactional
    public BeneficioResponse update(Integer id, BeneficioRequest request) { return save(find(id), request); }
    private BeneficioResponse save(Beneficio entity, BeneficioRequest request) {
        entity.setDataCenter(request.dataCenterId() == null ? null : dataCenterRepository.findById(request.dataCenterId()).orElseThrow(() -> new ResourceNotFoundException("DataCenter", request.dataCenterId())));
        entity.setTipo(request.tipo());
        entity.setDescricao(request.descricao());
        entity.setFonte(request.fonteId() == null ? null : fonteRepository.findById(request.fonteId()).orElseThrow(() -> new ResourceNotFoundException("Fonte", request.fonteId())));
        return BeneficioMapper.toResponse(repository.saveAndFlush(entity));
    }
    @Transactional
    public void delete(Integer id) { repository.delete(find(id)); repository.flush(); }
}
