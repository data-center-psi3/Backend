package data_centers.brasil.repository;
import data_centers.brasil.model.Beneficio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
public interface BeneficioRepository extends JpaRepository<Beneficio, Integer>, JpaSpecificationExecutor<Beneficio> {
    boolean existsByDataCenterId(Integer id);
    boolean existsByFonteId(Integer id);
}
