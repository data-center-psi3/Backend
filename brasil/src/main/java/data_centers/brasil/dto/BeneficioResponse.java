package data_centers.brasil.dto;
import java.math.BigDecimal;
import java.time.LocalDate;
public record BeneficioResponse(Integer id, Integer dataCenterId, String tipo, String descricao, Integer fonteId) {}
