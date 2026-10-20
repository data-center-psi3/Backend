package data_centers.brasil.repository;
import data_centers.brasil.model.ConteudoPesquisa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
public interface ConteudoPesquisaRepository extends JpaRepository<ConteudoPesquisa, Integer>, JpaSpecificationExecutor<ConteudoPesquisa> {
    boolean existsByDataCenterId(Integer id);
    boolean existsByFonteId(Integer id);
}
