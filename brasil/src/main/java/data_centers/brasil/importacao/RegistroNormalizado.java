package data_centers.brasil.importacao;

import data_centers.brasil.dto.CargaAviso;
import data_centers.brasil.dto.DataCenterRequest;
import data_centers.brasil.model.DataCenterImportacao;

import java.time.LocalDate;
import java.util.List;

public record RegistroNormalizado(
    DataCenterImportacao original, DataCenterRequest dataCenter,
    LocalDate dataLevantamento, String conteudoPesquisa, List<CargaAviso> avisos
) {}
