package data_centers.brasil.dto;
import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.validation.constraints.*;
public record DataCenterRequest(
    @NotBlank @Size(max = 150) String nome,
    @Size(max = 150) String empresa,
    @NotBlank @Size(max = 100) String cidade,
    @NotBlank @Size(max = 2) @Pattern(regexp = "[A-Z]{2}") String estado,
    @Size(max = 50) String pais,
    @Digits(integer = 3, fraction = 7) @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
    @Digits(integer = 3, fraction = 7) @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
    String descricao,
    @Digits(integer = 8, fraction = 2) @PositiveOrZero BigDecimal potenciaMw,
    @Digits(integer = 10, fraction = 2) @PositiveOrZero BigDecimal areaM2,
    @Size(max = 100) String tipoInstalacao,
    @Size(max = 30) String statusValidacao,
    LocalDate dataValidacao
) {}
