package data_centers.brasil.model;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
@Getter @Setter
@Entity @Table(name = "fonte")
public class Fonte {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(name = "instituicao", nullable = false, length = 150)
    private String instituicao;
    @Column(name = "titulo", nullable = false, length = 200)
    private String titulo;
    @Column(name = "url", columnDefinition = "text")
    private String url;
    @Column(name = "data_publicacao")
    private LocalDate dataPublicacao;
    @Column(name = "data_consulta", nullable = false)
    private LocalDate dataConsulta;
}
