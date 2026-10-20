package data_centers.brasil.model;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name = "data_center_fonte") @Getter @Setter @NoArgsConstructor
public class DataCenterFonte {
    @EmbeddedId private DataCenterFonteId id;
    @MapsId("dataCenterId") @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "data_center_id") private DataCenter dataCenter;
    @MapsId("fonteId") @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fonte_id") private Fonte fonte;
    public DataCenterFonte(DataCenter dataCenter, Fonte fonte) {
        this.id = new DataCenterFonteId(dataCenter.getId(), fonte.getId());
        this.dataCenter = dataCenter;
        this.fonte = fonte;
    }
}
