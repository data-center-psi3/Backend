package data_centers.brasil.dto;
import java.math.BigDecimal;
public record MapaResponse(Integer id, String nome, String empresa, String cidade, String estado,
    BigDecimal latitude, BigDecimal longitude, BigDecimal potenciaMw, String tipoInstalacao) {}
