package data_centers.brasil.dto;

import java.util.List;

public record CargaResponse(
    String arquivo, int totalLinhas, int dataCentersCriados, int dataCentersReutilizados,
    int fontesCriadas, int indicadoresCriados, int conteudosCriados,
    int linhasBrutasCriadas, int registrosIgnorados, List<CargaAviso> avisos
) {}
