package data_centers.brasil.dto;
import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.validation.constraints.*;
public record ConteudoPesquisaRequest(
    @Positive Integer dataCenterId,
    @NotBlank @Size(max = 200) String titulo,
    @NotBlank String conteudo,
    @Size(max = 50) String categoria,
    @NotNull @Positive Integer fonteId,
    LocalDate dataPublicacao
) {}
