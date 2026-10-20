package data_centers.brasil.dto;

import java.util.List;

public record CargaPreviaResponse(
    String arquivo, int totalLinhas, long registrosComCoordenadas,
    long registrosComPotenciaNumerica, List<DataCenterRequest> amostra, List<CargaAviso> avisos
) {}
