package data_centers.brasil.dto;
import java.math.BigDecimal;
public record DashboardResponse(String cidade, long quantidadeDataCenters, BigDecimal potenciaTotalMw) {}
