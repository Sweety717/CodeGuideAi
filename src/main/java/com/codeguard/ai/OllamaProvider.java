package com.codeguard.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bean name "ollama" - free/local, no API key. Requires the model already pulled (`ollama pull ...`). */
@Component("ollama")
public class OllamaProvider implements AiProvider {

    private final String baseUrl;
    private final String model;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OllamaProvider(@Value("${ollama.url:}") String baseUrl, @Value("${ollama.model:}") String model) {
        this.baseUrl = baseUrl;
        this.model = model;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt, boolean jsonMode) throws Exception {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("ollama.url is not set in application.properties.");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalStateException("ollama.model is not set in application.properties.");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("stream", false);
        if (jsonMode) body.put("format", "json");
        body.put("options", Map.of("num_predict", 3000));
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String url = baseUrl.replaceAll("/$", "") + "/api/chat";

        String raw;
        try {
            raw = restTemplate.postForObject(url, new HttpEntity<>(body, headers), String.class);
        } catch (RestClientException ex) {
            throw new IllegalStateException(
                    "Couldn't reach Ollama at " + baseUrl + ". Is it running? (ollama serve)", ex);
        }

        JsonNode root = objectMapper.readTree(raw);
        return root.path("message").path("content").asText();
    }
}
