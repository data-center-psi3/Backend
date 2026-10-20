package data_centers.brasil.model;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
@Getter @Setter
@Entity @Table(name = "data_center")
public class DataCenter {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(name = "nome", nullable = false, length = 150)
    private String nome;
    @Column(name = "empresa", length = 150)
    private String empresa;
    @Column(name = "cidade", nullable = false, length = 100)
    private String cidade;
    @Column(name = "estado", nullable = false, length = 2)
    private String estado;
    @Column(name = "pais", length = 50)
    private String pais;
    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;
    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;
    @Column(name = "descricao", columnDefinition = "text")
    private String descricao;
    @Column(name = "potencia_mw", precision = 10, scale = 2)
    private BigDecimal potenciaMw;
    @Column(name = "area_m2", precision = 12, scale = 2)
    private BigDecimal areaM2;
    @Column(name = "tipo_instalacao", length = 100)
    private String tipoInstalacao;
    @Column(name = "status_validacao", length = 30)
    private String statusValidacao;
    @Column(name = "data_validacao")
    private LocalDate dataValidacao;
}
