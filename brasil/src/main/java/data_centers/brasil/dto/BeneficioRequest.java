package data_centers.brasil.dto;
import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.validation.constraints.*;
public record BeneficioRequest(
    @NotNull @Positive Integer dataCenterId,
    @NotBlank @Size(max = 50) String tipo,
    @NotBlank String descricao,
    @Positive Integer fonteId
) {}
