package data_centers.brasil.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class AgentService {

    private final RestClient restClient;

    @Value("${gemini.api.model}")
    private String model;

    public AgentService(
            @Value("${gemini.api.key}") String apiKey,
            @Value("${gemini.api.url}") String baseUrl
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("x-goog-api-key", apiKey)
                .build();
    }

    public String perguntar(String pergunta) {

        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of(
                                "parts", List.of(
                                        Map.of("text", pergunta)
                                )
                        )
                )
        );

        Map response = restClient.post()
                .uri("/models/{model}:generateContent", model)
                .body(body)
                .retrieve()
                .body(Map.class);

        if (response == null) {
            throw new IllegalStateException(
                    "O Gemini retornou uma resposta vazia."
            );
        }

        List<Map<String, Object>> candidates =
                (List<Map<String, Object>>) response.get("candidates");

        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalStateException(
                    "O Gemini não retornou candidatos."
            );
        }

        Map<String, Object> content =
                (Map<String, Object>) candidates.get(0).get("content");

        List<Map<String, Object>> parts =
                (List<Map<String, Object>>) content.get("parts");

        if (parts == null || parts.isEmpty()) {
            throw new IllegalStateException(
                    "O Gemini não retornou uma resposta de texto."
            );
        }

        return (String) parts.get(0).get("text");
    }
}