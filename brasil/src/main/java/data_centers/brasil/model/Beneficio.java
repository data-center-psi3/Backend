package data_centers.brasil.model;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
@Getter @Setter
@Entity @Table(name = "beneficio")
public class Beneficio {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "data_center_id", nullable = false)
    private DataCenter dataCenter;
    @Column(name = "tipo", nullable = false, length = 50)
    private String tipo;
    @Column(name = "descricao", nullable = false, columnDefinition = "text")
    private String descricao;
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "fonte_id", nullable = true)
    private Fonte fonte;
}
