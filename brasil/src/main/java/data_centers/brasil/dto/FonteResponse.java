package data_centers.brasil.dto;
import java.math.BigDecimal;
import java.time.LocalDate;
public record FonteResponse(Integer id, String instituicao, String titulo, String url, LocalDate dataPublicacao, LocalDate dataConsulta) {}
