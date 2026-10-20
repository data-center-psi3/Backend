package data_centers.brasil.dto;
import java.math.BigDecimal;
import java.time.LocalDate;
public record IndicadorAmbientalResponse(Integer id, Integer dataCenterId, String tipo, BigDecimal valor, String unidade, String periodo, Integer fonteId, String observacoes) {}
