package data_centers.brasil.dto;
import java.math.BigDecimal;
import java.time.LocalDate;
public record ConteudoPesquisaResponse(Integer id, Integer dataCenterId, String titulo, String conteudo, String categoria, Integer fonteId, LocalDate dataPublicacao) {}
