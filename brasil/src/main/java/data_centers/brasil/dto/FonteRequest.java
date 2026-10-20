package data_centers.brasil.dto;
import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.validation.constraints.*;
public record FonteRequest(
    @NotBlank @Size(max = 150) String instituicao,
    @NotBlank @Size(max = 200) String titulo,
    String url,
    LocalDate dataPublicacao,
    @NotNull LocalDate dataConsulta
) {}
