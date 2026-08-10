package com.codeguard.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bean name "openai" - matched against ai.provider=openai. Model name comes ENTIRELY from openai.model. */
@Component("openai")
public class OpenAiProvider implements AiProvider {

    private static final String URL = "https://api.openai.com/v1/chat/completions";

    private final String apiKey;
    private final String model;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAiProvider(@Value("${openai.api.key:}") String apiKey,
                           @Value("${openai.model:}") String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt, boolean jsonMode) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("openai.api.key is not set in application.properties.");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalStateException("openai.model is not set in application.properties.");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        body.put("temperature", 0.2);
        body.put("max_tokens", 3000);
        if (jsonMode) {
            body.put("response_format", Map.of("type", "json_object"));
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        String raw;
        try {
            raw = restTemplate.postForObject(URL, new HttpEntity<>(body, headers), String.class);
        } catch (HttpClientErrorException ex) {
            throw new IllegalStateException(
                    "OpenAI API error (" + ex.getStatusCode() + "): " + ex.getResponseBodyAsString(), ex);
        } catch (RestClientException ex) {
            throw new IllegalStateException("Couldn't reach OpenAI's API. Check your network connection.", ex);
        }

        JsonNode root = objectMapper.readTree(raw);
        return root.path("choices").get(0).path("message").path("content").asText();
    }
}
