package data_centers.brasil.repository;

import data_centers.brasil.model.News;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsRepository
        extends JpaRepository<News, Long> {

    boolean existsByUrl(String url);

    java.util.Optional<News> findByUrl(String url);
}
