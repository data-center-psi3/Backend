package data_centers.brasil.repository;
import data_centers.brasil.model.Fonte;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
public interface FonteRepository extends JpaRepository<Fonte, Integer>, JpaSpecificationExecutor<Fonte> {
    java.util.List<Fonte> findByInstituicaoAndTitulo(String instituicao, String titulo);
}
