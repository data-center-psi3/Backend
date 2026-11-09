package data_centers.brasil.agent.client;

import data_centers.brasil.agent.context.AgentContext;
import data_centers.brasil.agent.tool.AgentToolDeclarations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import java.util.*;

/** Gemini transport, protocol validation, timeouts and bounded transient retries. */
@Component
public class GeminiClient {
    private final RestClient client;
    private final String model;
    private final AgentContext context;
    private final AgentToolDeclarations tools;

    @Autowired
    public GeminiClient(@Value("${gemini.api.key:}") String key,
                        @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta}") String url,
                        @Value("${gemini.api.model:gemini-2.5-flash}") String model,
                        AgentContext context, AgentToolDeclarations tools) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(30000);
        this.client = RestClient.builder().baseUrl(url).defaultHeader("x-goog-api-key", key).requestFactory(factory).build();
        this.model = model;
        this.context = context;
        this.tools = tools;
    }

    public GeminiClient(RestClient client, String model, AgentContext context, AgentToolDeclarations tools) {
        this.client = client;
        this.model = model;
        this.context = context;
        this.tools = tools;
    }

    public Map<?, ?> generate(List<Object> history, String mode) {
        var body = Map.of("systemInstruction", Map.of("parts", List.of(Map.of("text", context.instrucoes()))),
            "contents", history, "tools", List.of(Map.of("functionDeclarations", tools.declarations())),
            "toolConfig", Map.of("functionCallingConfig", Map.of("mode", mode)),
            "generationConfig", Map.of("temperature", 0.1, "maxOutputTokens", 2048));
        for (int attempt = 0; ; attempt++) {
            try {
                Map<?, ?> response = client.post().uri("/models/{model}:generateContent", model).body(body).retrieve().body(Map.class);
                if (response == null || !(response.get("candidates") instanceof List<?> list) || list.isEmpty()
                    || !(list.getFirst() instanceof Map<?, ?> candidate) || !(candidate.get("content") instanceof Map<?, ?> content))
                    throw new IllegalStateException();
                if (candidate.get("finishReason") instanceof String reason && !reason.equals("STOP")) throw new IllegalStateException();
                return content;
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                if (attempt >= 2 || !(status == 429 || status == 500 || status == 502 || status == 503 || status == 504)) throw e;
            } catch (ResourceAccessException e) {
                if (attempt >= 2) throw e;
            }
            try { Thread.sleep(250L << attempt); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(); }
        }
    }

}
