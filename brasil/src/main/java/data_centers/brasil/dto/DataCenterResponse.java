package data_centers.brasil.dto;
import java.math.BigDecimal;
import java.time.LocalDate;
public record DataCenterResponse(Integer id, String nome, String empresa, String cidade, String estado, String pais, BigDecimal latitude, BigDecimal longitude, String descricao, BigDecimal potenciaMw, BigDecimal areaM2, String tipoInstalacao, String statusValidacao, LocalDate dataValidacao) {}
