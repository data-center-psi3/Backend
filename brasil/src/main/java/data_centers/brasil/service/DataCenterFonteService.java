package data_centers.brasil.service;
import data_centers.brasil.filter.*;
import org.springframework.util.MultiValueMap;
import data_centers.brasil.dto.FonteResponse;
import data_centers.brasil.mapper.FonteMapper;
import data_centers.brasil.model.*;
import data_centers.brasil.repository.*;
import data_centers.brasil.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class DataCenterFonteService {
    private final DataCenterFonteRepository repository;
    private final DataCenterRepository dataCenters;
    private final FonteRepository fontes;
    private final QueryFilterParser filterParser;
    public Page<FonteResponse> list(Integer id, MultiValueMap<String, String> filters, Pageable pageable) {
        if (!dataCenters.existsById(id)) throw new ResourceNotFoundException("DataCenter", id);
        var specification = JpaFilters.<DataCenterFonte>specification(filterParser.parse(filters, FilterResource.FONTE), "fonte");
        specification = specification.and((root, query, cb) -> cb.equal(root.get("dataCenter").get("id"), id));
        return repository.findAll(specification, pageable).map(link -> FonteMapper.toResponse(link.getFonte()));
    }
    @Transactional
    public void link(Integer dcId, Integer fonteId) {
        var dc = dataCenters.findById(dcId).orElseThrow(() -> new ResourceNotFoundException("DataCenter", dcId));
        var fonte = fontes.findById(fonteId).orElseThrow(() -> new ResourceNotFoundException("Fonte", fonteId));
        var id = new DataCenterFonteId(dcId, fonteId);
        if (!repository.existsById(id)) repository.saveAndFlush(new DataCenterFonte(dc, fonte));
    }
    @Transactional
    public void unlink(Integer dcId, Integer fonteId) {
        var id = new DataCenterFonteId(dcId, fonteId);
        var link = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Vínculo de fonte", fonteId));
        repository.delete(link);
        repository.flush();
    }
}
