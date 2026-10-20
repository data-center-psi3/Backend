package data_centers.brasil.service;
import data_centers.brasil.filter.*;
import org.springframework.util.MultiValueMap;
import data_centers.brasil.dto.*;
import data_centers.brasil.model.*;
import data_centers.brasil.repository.*;
import data_centers.brasil.mapper.ConteudoPesquisaMapper;
import data_centers.brasil.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class ConteudoPesquisaService {
    private final ConteudoPesquisaRepository repository;
    private final QueryFilterParser filterParser;
    private final DataCenterRepository dataCenterRepository;
    private final FonteRepository fonteRepository;
    public Page<ConteudoPesquisaResponse> list(MultiValueMap<String, String> filters, Pageable pageable) {
        return repository.findAll(JpaFilters.<ConteudoPesquisa>specification(filterParser.parse(filters, FilterResource.CONTEUDO_PESQUISA)), pageable).map(ConteudoPesquisaMapper::toResponse);
    }
    public ConteudoPesquisaResponse get(Integer id) { return ConteudoPesquisaMapper.toResponse(find(id)); }
    private ConteudoPesquisa find(Integer id) { return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("ConteudoPesquisa", id)); }
    @Transactional
    public ConteudoPesquisaResponse create(ConteudoPesquisaRequest request) { return save(new ConteudoPesquisa(), request); }
    @Transactional
    public ConteudoPesquisaResponse update(Integer id, ConteudoPesquisaRequest request) { return save(find(id), request); }
    private ConteudoPesquisaResponse save(ConteudoPesquisa entity, ConteudoPesquisaRequest request) {
        entity.setDataCenter(request.dataCenterId() == null ? null : dataCenterRepository.findById(request.dataCenterId()).orElseThrow(() -> new ResourceNotFoundException("DataCenter", request.dataCenterId())));
        entity.setTitulo(request.titulo());
        entity.setConteudo(request.conteudo());
        entity.setCategoria(request.categoria());
        entity.setFonte(request.fonteId() == null ? null : fonteRepository.findById(request.fonteId()).orElseThrow(() -> new ResourceNotFoundException("Fonte", request.fonteId())));
        entity.setDataPublicacao(request.dataPublicacao());
        return ConteudoPesquisaMapper.toResponse(repository.saveAndFlush(entity));
    }
    @Transactional
    public void delete(Integer id) { repository.delete(find(id)); repository.flush(); }
}
