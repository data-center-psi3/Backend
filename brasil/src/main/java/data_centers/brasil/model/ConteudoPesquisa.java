package data_centers.brasil.model;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
@Getter @Setter
@Entity @Table(name = "conteudo_pesquisa")
public class ConteudoPesquisa {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "data_center_id", nullable = true)
    private DataCenter dataCenter;
    @Column(name = "titulo", nullable = false, length = 200)
    private String titulo;
    @Column(name = "conteudo", nullable = false, columnDefinition = "text")
    private String conteudo;
    @Column(name = "categoria", length = 50)
    private String categoria;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fonte_id", nullable = false)
    private Fonte fonte;
    @Column(name = "data_publicacao")
    private LocalDate dataPublicacao;
}
