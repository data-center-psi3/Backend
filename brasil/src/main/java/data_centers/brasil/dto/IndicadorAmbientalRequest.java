package data_centers.brasil.dto;
import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.validation.constraints.*;
public record IndicadorAmbientalRequest(
    @NotNull @Positive Integer dataCenterId,
    @NotBlank @Size(max = 100) String tipo,
    @Digits(integer = 11, fraction = 4) BigDecimal valor,
    @Size(max = 50) String unidade,
    @Size(max = 30) String periodo,
    @Positive Integer fonteId,
    String observacoes
) {}
