package data_centers.brasil.service;
import data_centers.brasil.filter.*;
import org.springframework.util.MultiValueMap;
import data_centers.brasil.dto.*;
import data_centers.brasil.model.*;
import data_centers.brasil.repository.*;
import data_centers.brasil.mapper.FonteMapper;
import data_centers.brasil.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class FonteService {
    private final FonteRepository repository;
    private final QueryFilterParser filterParser;
    private final IndicadorAmbientalRepository indicadores;
    private final BeneficioRepository beneficios;
    private final ConteudoPesquisaRepository conteudos;
    private final DataCenterFonteRepository vinculos;

    public Page<FonteResponse> list(MultiValueMap<String, String> filters, Pageable pageable) {
        return repository.findAll(JpaFilters.<Fonte>specification(filterParser.parse(filters, FilterResource.FONTE)), pageable).map(FonteMapper::toResponse);
    }
    public FonteResponse get(Integer id) { return FonteMapper.toResponse(find(id)); }
    private Fonte find(Integer id) { return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Fonte", id)); }
    @Transactional
    public FonteResponse create(FonteRequest request) { return save(new Fonte(), request); }
    @Transactional
    public FonteResponse update(Integer id, FonteRequest request) { return save(find(id), request); }
    private FonteResponse save(Fonte entity, FonteRequest request) {
        entity.setInstituicao(request.instituicao());
        entity.setTitulo(request.titulo());
        entity.setUrl(request.url());
        entity.setDataPublicacao(request.dataPublicacao());
        entity.setDataConsulta(request.dataConsulta());
        return FonteMapper.toResponse(repository.saveAndFlush(entity));
    }
    @Transactional
    public void delete(Integer id) {
        var entity = find(id);
        if (indicadores.existsByFonteId(id) || beneficios.existsByFonteId(id)
            || conteudos.existsByFonteId(id) || vinculos.existsByIdFonteId(id)) {
            throw new data_centers.brasil.exception.ResourceConflictException("Fonte possui registros vinculados. Remova os vínculos antes de excluir.");
        }
        repository.delete(entity);
        repository.flush();
    }
}
