package data_centers.brasil.controller;

import data_centers.brasil.model.News;
import data_centers.brasil.repository.NewsRepository;
import data_centers.brasil.service.NewsCollectorService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/news")
public class NewsController {

    private final NewsRepository repository;
    private final NewsCollectorService collector;

    public NewsController(
            NewsRepository repository,
            NewsCollectorService collector
    ) {
        this.repository = repository;
        this.collector = collector;
    }

    @GetMapping
    public Page<News> list(
            @PageableDefault(
                    size = 12,
                    sort = "dataPublicacao",
                    direction = Sort.Direction.DESC
            ) Pageable pageable
    ) {
        return repository.findAll(pageable);
    }

    @PostMapping("/sync")
    public ResponseEntity<Map<String, Integer>> sync()
            throws Exception {

        int saved = collector.collect();

        return ResponseEntity.ok(
                Map.of("noticiasAdicionadas", saved)
        );
    }
}