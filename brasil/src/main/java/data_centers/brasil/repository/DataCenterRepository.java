package data_centers.brasil.repository;
import data_centers.brasil.model.DataCenter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
public interface DataCenterRepository extends JpaRepository<DataCenter, Integer>, JpaSpecificationExecutor<DataCenter> {
    java.util.List<DataCenter> findByNomeAndEmpresaAndCidadeAndEstado(String nome, String empresa, String cidade, String estado);
}
