package data_centers.brasil.service;


import data_centers.brasil.dto.DataCenterResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class AgentDataBaseService {

    private final DataCenterService dataCenterService;

    public AgentDataBaseService

                (DataCenterService dataCenterService) {
        this.dataCenterService = dataCenterService;
    }

    public Map<String, Object> consultarResumo() {

        var filtros = new LinkedMultiValueMap<String, String>();

        var pagina = dataCenterService.list(
                filtros,
                PageRequest.of(0, 20)
        );

        Map<String, Object> resultado = new LinkedHashMap<>();

        resultado.put("totalDataCenters", pagina.getTotalElements());
        resultado.put("dataCentersConsultados", pagina.getContent());
        resultado.put("limiteRegistros", 20);
        resultado.put("dadosParciais", pagina.hasNext());

        return resultado;
    }
}
