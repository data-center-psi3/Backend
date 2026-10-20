package data_centers.brasil.repository;
import data_centers.brasil.model.IndicadorAmbiental;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
public interface IndicadorAmbientalRepository extends JpaRepository<IndicadorAmbiental, Integer>, JpaSpecificationExecutor<IndicadorAmbiental> {
    boolean existsByDataCenterId(Integer id);
    boolean existsByFonteId(Integer id);
}
