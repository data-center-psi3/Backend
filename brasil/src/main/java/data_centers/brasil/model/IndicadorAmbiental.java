package data_centers.brasil.model;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
@Getter @Setter
@Entity @Table(name = "indicador_ambiental")
public class IndicadorAmbiental {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "data_center_id", nullable = false)
    private DataCenter dataCenter;
    @Column(name = "tipo", nullable = false, length = 100)
    private String tipo;
    @Column(name = "valor", precision = 15, scale = 4)
    private BigDecimal valor;
    @Column(name = "unidade", length = 50)
    private String unidade;
    @Column(name = "periodo", length = 30)
    private String periodo;
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "fonte_id", nullable = true)
    private Fonte fonte;
    @Column(name = "observacoes", columnDefinition = "text")
    private String observacoes;
}
