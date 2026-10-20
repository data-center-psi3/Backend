package data_centers.brasil.model;
import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;
@Embeddable @Getter @Setter @EqualsAndHashCode @NoArgsConstructor @AllArgsConstructor
public class DataCenterFonteId implements Serializable {
    @Column(name = "data_center_id") private Integer dataCenterId;
    @Column(name = "fonte_id") private Integer fonteId;
}
