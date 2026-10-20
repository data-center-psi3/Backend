package data_centers.brasil.service;
import data_centers.brasil.filter.*;
import org.springframework.util.MultiValueMap;
import data_centers.brasil.dto.*;
import data_centers.brasil.mapper.DataCenterMapper;
import data_centers.brasil.model.DataCenter;
import data_centers.brasil.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import java.util.*;
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class ConsultaService {
    private final DataCenterRepository repository;
    private final ConsultaRepository consultas;
    private final QueryFilterParser filterParser;
    public Page<DataCenterResponse> filter(MultiValueMap<String, String> filters, Pageable pageable) {
        return repository.findAll(JpaFilters.<DataCenter>specification(filterParser.parse(filters, FilterResource.DATA_CENTER)), pageable).map(DataCenterMapper::toResponse);
    }
    public Page<MapaResponse> mapa(MultiValueMap<String, String> filters, Pageable pageable) {
        var specification = JpaFilters.<DataCenter>specification(filterParser.parse(filters, FilterResource.DATA_CENTER));
        specification = specification.and((root, query, cb) -> cb.and(cb.isNotNull(root.get("latitude")), cb.isNotNull(root.get("longitude"))));
        return repository.findAll(specification, pageable).map(dc ->
            new MapaResponse(dc.getId(), dc.getNome(), dc.getEmpresa(), dc.getCidade(), dc.getEstado(),
                dc.getLatitude(), dc.getLongitude(), dc.getPotenciaMw(), dc.getTipoInstalacao()));
    }
    public List<DashboardResponse> dashboard(MultiValueMap<String, String> filters) {
        return consultas.dashboard(filterParser.parse(filters, FilterResource.DASHBOARD));
    }
}
