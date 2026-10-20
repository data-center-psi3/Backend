package data_centers.brasil.repository;
import data_centers.brasil.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
public interface DataCenterFonteRepository extends JpaRepository<DataCenterFonte, DataCenterFonteId>, JpaSpecificationExecutor<DataCenterFonte> {
    Page<DataCenterFonte> findByIdDataCenterId(Integer dataCenterId, Pageable pageable);
    boolean existsByIdDataCenterId(Integer id);
    boolean existsByIdFonteId(Integer id);
    java.util.List<DataCenterFonte> findByIdFonteId(Integer id);
}
